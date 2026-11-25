package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * FE-10 best-seller queries over committed source tables (same precedent as {@link JdbcReportingSourceAdapter}).
 * Products use the facts of the retail revenue KPI (paid VND branch orders by payment time); packages use the
 * "confirmed" facts of the workshop ticket trend (ticket status by the time the booking group was placed, branch of the
 * first session). Source owners review these queries (FE-08 retail, FE-02 catalogue, FE-03 workshop).
 */
@Component
public class JdbcReportingTopSellerAdapter implements ReportingTopSellerPort {

    private static final String TOP_PRODUCTS = """
        SELECT p.id AS product_id, p.name AS name, SUM(i.quantity) AS quantity, COALESCE(SUM(i.line_total), 0) AS revenue
        FROM retail_order_items i
        JOIN retail_orders o ON o.id = i.retail_order_id
        JOIN retail_grouped_checkouts c ON c.id = o.grouped_checkout_id
        JOIN catalogue_variants v ON v.id = i.variant_id
        JOIN catalogue_products p ON p.id = v.product_id
        WHERE o.payment_state = 'PAID' AND c.currency = 'VND'
          AND o.paid_at >= :from AND o.paid_at < :to AND o.location_id IN (:ids)
        GROUP BY p.id, p.name
        ORDER BY quantity DESC, revenue DESC, p.id
        LIMIT :limit
        """;

    private static final String TOP_PACKAGES = """
        SELECT l.package_id AS package_id,
               COALESCE((SELECT wp.name FROM workshop_packages wp WHERE wp.id = l.package_id), MAX(l.package_name_snapshot)) AS name,
               COUNT(*) AS tickets, COALESCE(SUM(l.price_per_participant_vnd), 0) AS revenue
        FROM ticket_bookings tb
        JOIN booking_groups g ON g.id = tb.group_id
        JOIN booking_group_lines l ON l.group_id = tb.group_id AND l.line_index = tb.line_index
        JOIN booking_group_line_sessions s ON s.line_id = l.id AND s.ordinal = 1
        WHERE tb.ticket_status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW')
          AND g.created_at >= :from AND g.created_at < :to AND s.location_reference IN (:ids)
        GROUP BY l.package_id
        ORDER BY tickets DESC, revenue DESC, l.package_id
        LIMIT :limit
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcReportingTopSellerAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSales> topProducts(Collection<Long> locationIds, Instant from, Instant to, int limit) {
        if (locationIds.isEmpty()) return List.of();
        return jdbc.query(TOP_PRODUCTS, params(locationIds, from, to, limit), (rs, row) -> new ProductSales(
            rs.getLong("product_id"), rs.getString("name"), rs.getLong("quantity"), whole(rs.getBigDecimal("revenue"))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackageSales> topPackages(Collection<Long> locationIds, Instant from, Instant to, int limit) {
        if (locationIds.isEmpty()) return List.of();
        return jdbc.query(TOP_PACKAGES, params(locationIds, from, to, limit), (rs, row) -> new PackageSales(
            rs.getLong("package_id"), rs.getString("name"), rs.getLong("tickets"), whole(rs.getBigDecimal("revenue"))));
    }

    private static MapSqlParameterSource params(Collection<Long> ids, Instant from, Instant to, int limit) {
        return new MapSqlParameterSource("ids", ids).addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to))
            .addValue("limit", limit);
    }

    private static BigInteger whole(BigDecimal amount) {
        return amount == null ? BigInteger.ZERO : amount.setScale(0, RoundingMode.DOWN).toBigIntegerExact();
    }
}
