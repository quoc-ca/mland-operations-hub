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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only aggregate queries over committed source tables, following the FE-11 review-eligibility precedent of a
 * feature-owned JDBC adapter. Each query aggregates in MySQL and returns one row per branch, so no raw orders,
 * payments or customer data cross into reporting. Source owners review these queries (FE-08 retail, FE-03 workshop).
 */
@Component
public class JdbcReportingSourceAdapter implements ReportingSourcePort {

    private static final String RETAIL = """
        SELECT o.location_id AS location_id, c.currency AS currency,
               COALESCE(SUM(o.payable_amount - o.shipping_charge), 0) AS revenue, COUNT(*) AS paid_orders
        FROM retail_orders o
        JOIN retail_grouped_checkouts c ON c.id = o.grouped_checkout_id
        WHERE o.payment_state = 'PAID' AND o.paid_at >= :from AND o.paid_at < :to AND o.location_id IN (:ids)
        GROUP BY o.location_id, c.currency
        """;

    /*
     * A folio is fully paid when FE-03 finalizes it (balance zero, nothing pending). Its revenue is the frozen
     * post-promotion amount (the deposit is inside it) plus confirmed charges. The fully-paid time is the
     * FOLIO_FINALIZED audit, else the latest confirmed collection or verified final payment, else the freeze time.
     * The branch is the first committed session of the booking, else the session of one of its tickets.
     */
    private static final String WORKSHOP = """
        SELECT f.location_id AS location_id, f.currency_code AS currency, COUNT(*) AS folios,
               COALESCE(SUM(f.total_vnd), 0) AS revenue
        FROM (
            SELECT b.currency_code,
                   b.post_promotion_amount_vnd + COALESCE((SELECT SUM(ch.amount_vnd) FROM settlement_charges ch
                        WHERE ch.bill_reference = b.bill_reference AND ch.lifecycle_status = 'CONFIRMED'), 0) AS total_vnd,
                   COALESCE(
                        (SELECT bsc.location_id FROM booking_session_commitments bsc
                         JOIN workshop_bookings wb ON wb.id = bsc.booking_id
                         WHERE wb.booking_code = b.booking_reference ORDER BY bsc.ordinal LIMIT 1),
                        (SELECT MIN(ws.location_id) FROM attendance_tickets t
                         JOIN ticket_session_entitlements e ON e.ticket_id = t.id
                         JOIN workshop_sessions ws ON ws.id = e.session_id
                         WHERE t.booking_reference = b.booking_reference)) AS location_id,
                   COALESCE(
                        (SELECT MAX(a.created_at) FROM settlement_business_audits a
                         WHERE a.aggregate_type = 'SETTLEMENT_FINAL_BILL' AND a.action_code = 'FOLIO_FINALIZED'
                           AND a.aggregate_reference = b.booking_reference),
                        GREATEST(
                            COALESCE((SELECT MAX(col.confirmed_at) FROM settlement_folio_collections col
                                      WHERE col.bill_reference = b.bill_reference AND col.status = 'CONFIRMED'), b.frozen_at),
                            COALESCE((SELECT MAX(ev.created_at) FROM settlement_evidence_conclusions ev
                                      WHERE ev.booking_reference = b.booking_reference
                                        AND ev.conclusion = 'FINAL_PAYMENT_VERIFIED'), b.frozen_at))) AS fully_paid_at
            FROM settlement_final_bills b
            WHERE b.lifecycle_status = 'FINALIZED'
        ) f
        WHERE f.fully_paid_at >= :from AND f.fully_paid_at < :to
        GROUP BY f.location_id, f.currency_code
        """;

    private static final String SESSIONS = """
        SELECT ws.location_id AS location_id, COUNT(*) AS sessions, COALESCE(SUM(ws.capacity), 0) AS capacity,
               COALESCE(SUM((SELECT COUNT(*) FROM ticket_session_entitlements e
                             WHERE e.session_id = ws.id AND e.attendance_state = 'CHECKED_IN')), 0) AS checked_in
        FROM workshop_sessions ws
        WHERE ws.state = 'ATTENDANCE_FINALIZED' AND ws.scheduled_start_at >= :from AND ws.scheduled_start_at < :to
          AND ws.location_id IN (:ids)
        GROUP BY ws.location_id
        """;

    private static final String NAMES = "SELECT id, display_name FROM workshop_locations WHERE id IN (:ids)";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcReportingSourceAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public List<RetailBranchRevenue> retailRevenue(Collection<Long> locationIds, Instant from, Instant to) {
        if (locationIds.isEmpty()) return List.of();
        return jdbc.query(RETAIL, params(locationIds, from, to), (rs, row) -> new RetailBranchRevenue(
            rs.getLong("location_id"), rs.getString("currency"), whole(rs.getBigDecimal("revenue")), rs.getLong("paid_orders")));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkshopRevenue workshopRevenue(Collection<Long> locationIds, Instant from, Instant to) {
        Set<Long> scope = Set.copyOf(locationIds);
        Map<Long, WorkshopBranchRevenue> branches = new LinkedHashMap<>();
        long[] unresolved = {0};
        long[] nonVnd = {0};
        jdbc.query(WORKSHOP, params(locationIds, from, to), rs -> {
            Object location = rs.getObject("location_id");
            long folios = rs.getLong("folios");
            if (location == null) { unresolved[0] += folios; return; }
            long locationId = ((Number) location).longValue();
            if (!scope.contains(locationId)) return;
            if (!"VND".equals(rs.getString("currency"))) { nonVnd[0] += folios; return; }
            BigInteger revenue = whole(rs.getBigDecimal("revenue"));
            branches.merge(locationId, new WorkshopBranchRevenue(locationId, revenue, folios),
                (a, b) -> new WorkshopBranchRevenue(locationId, a.revenue().add(b.revenue()), a.folioCount() + b.folioCount()));
        });
        return new WorkshopRevenue(new ArrayList<>(branches.values()), unresolved[0], nonVnd[0]);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionCapacity> completedSessions(Collection<Long> locationIds, Instant from, Instant to) {
        if (locationIds.isEmpty()) return List.of();
        return jdbc.query(SESSIONS, params(locationIds, from, to), (rs, row) -> new SessionCapacity(
            rs.getLong("location_id"), rs.getLong("sessions"), rs.getLong("capacity"), rs.getLong("checked_in")));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> branchNames(Collection<Long> locationIds) {
        if (locationIds.isEmpty()) return Map.of();
        Map<Long, String> names = new HashMap<>();
        jdbc.query(NAMES, new MapSqlParameterSource("ids", locationIds),
            rs -> { names.put(rs.getLong("id"), rs.getString("display_name")); });
        return names;
    }

    private static MapSqlParameterSource params(Collection<Long> ids, Instant from, Instant to) {
        return new MapSqlParameterSource("ids", ids).addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to));
    }

    private static BigInteger whole(BigDecimal amount) {
        return amount == null ? BigInteger.ZERO : amount.setScale(0, RoundingMode.DOWN).toBigIntegerExact();
    }
}
