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
 * FE-10 trend queries over committed source tables (same FE-11 precedent as {@link JdbcReportingSourceAdapter}).
 * Each query groups by {@code TIMESTAMPDIFF(HOUR, :from, time)}: whole hours since the start of the period, which map
 * exactly onto Vietnam days and months (UTC+7, no daylight saving). Source owners review these queries (FE-08 retail,
 * FE-03 workshop).
 */
@Component
public class JdbcReportingTrendAdapter implements ReportingTrendPort {

    /** Orders placed in the period; still unpaid orders are placed minus the three outcomes. */
    private static final String RETAIL_ORDERS = """
        SELECT TIMESTAMPDIFF(HOUR, :from, o.created_at) AS h, COUNT(*) AS placed,
               COALESCE(SUM(o.payment_state = 'PAID'), 0) AS paid,
               COALESCE(SUM(o.payment_state = 'CANCELLED'), 0) AS cancelled,
               COALESCE(SUM(o.payment_state = 'EXPIRED'), 0) AS expired
        FROM retail_orders o
        WHERE o.created_at >= :from AND o.created_at < :to AND o.location_id IN (:ids)
        GROUP BY h
        """;

    /** Same facts as the retail revenue KPI, by hour of payment. */
    private static final String RETAIL_REVENUE = """
        SELECT TIMESTAMPDIFF(HOUR, :from, o.paid_at) AS h, COUNT(*) AS n,
               COALESCE(SUM(o.payable_amount - o.shipping_charge), 0) AS revenue
        FROM retail_orders o
        JOIN retail_grouped_checkouts c ON c.id = o.grouped_checkout_id
        WHERE o.payment_state = 'PAID' AND c.currency = 'VND'
          AND o.paid_at >= :from AND o.paid_at < :to AND o.location_id IN (:ids)
        GROUP BY h
        """;

    /*
     * One row per 003.1 ticket of a group placed in the period; the branch is the first session of its line. A
     * ticket is confirmed once paid (also when it later completed or was a no-show), cancelled when it or its group
     * was cancelled, abandoned when the group expired unpaid. Anything else is still waiting for payment.
     */
    private static final String WORKSHOP_BOOKINGS = """
        SELECT TIMESTAMPDIFF(HOUR, :from, g.created_at) AS h, COUNT(*) AS booked,
               COALESCE(SUM(tb.ticket_status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW')), 0) AS confirmed,
               COALESCE(SUM(tb.ticket_status = 'CANCELLED'
                            OR (g.group_status = 'CANCELLED' AND tb.ticket_status NOT IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW'))), 0) AS cancelled,
               COALESCE(SUM(tb.ticket_status <> 'CANCELLED' AND g.group_status <> 'CANCELLED'
                            AND (tb.ticket_status = 'EXPIRED' OR g.group_status = 'EXPIRED')
                            AND tb.ticket_status NOT IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW')), 0) AS abandoned
        FROM ticket_bookings tb
        JOIN booking_groups g ON g.id = tb.group_id
        JOIN booking_group_lines l ON l.group_id = tb.group_id AND l.line_index = tb.line_index
        JOIN booking_group_line_sessions s ON s.line_id = l.id AND s.ordinal = 1
        WHERE g.created_at >= :from AND g.created_at < :to AND s.location_reference IN (:ids)
        GROUP BY h
        """;

    /*
     * One row per participant place: a ticket's entitlement to one session (a two-session package counts twice). A
     * cancelled, revoked or refunded ticket is counted apart and never as registered. Draft sessions are not sold.
     */
    private static final String ATTENDANCE = """
        SELECT TIMESTAMPDIFF(HOUR, :from, ws.scheduled_start_at) AS h,
               COALESCE(SUM(t.validity_state = 'ACTIVE'), 0) AS registered,
               COALESCE(SUM(t.validity_state = 'ACTIVE' AND e.attendance_state = 'CHECKED_IN'), 0) AS checked_in,
               COALESCE(SUM(t.validity_state = 'ACTIVE' AND e.attendance_state = 'NO_SHOW'), 0) AS no_show,
               COALESCE(SUM(t.validity_state <> 'ACTIVE'), 0) AS cancelled
        FROM ticket_session_entitlements e
        JOIN attendance_tickets t ON t.id = e.ticket_id
        JOIN workshop_sessions ws ON ws.id = e.session_id
        WHERE ws.scheduled_start_at >= :from AND ws.scheduled_start_at < :to AND ws.location_id IN (:ids)
          AND ws.state <> 'DRAFT'
        GROUP BY h
        """;

    private static final String WORKSHOP_REVENUE = "SELECT TIMESTAMPDIFF(HOUR, :from, f.fully_paid_at) AS h, "
        + "COUNT(*) AS n, COALESCE(SUM(f.total_vnd), 0) AS revenue FROM (" + WorkshopFolioSql.FINALIZED_FOLIOS
        + ") f WHERE f.fully_paid_at >= :from AND f.fully_paid_at < :to AND f.currency_code = 'VND' "
        + "AND f.location_id IN (:ids) GROUP BY h";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcReportingTrendAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public List<RetailOrderHour> retailOrders(Collection<Long> locationIds, Instant from, Instant to) {
        if (locationIds.isEmpty()) return List.of();
        return jdbc.query(RETAIL_ORDERS, params(locationIds, from, to), (rs, row) -> new RetailOrderHour(
            rs.getLong("h"), rs.getLong("placed"), rs.getLong("paid"), rs.getLong("cancelled"), rs.getLong("expired")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevenueHour> retailRevenue(Collection<Long> locationIds, Instant from, Instant to) {
        if (locationIds.isEmpty()) return List.of();
        return jdbc.query(RETAIL_REVENUE, params(locationIds, from, to), (rs, row) -> new RevenueHour(
            rs.getLong("h"), whole(rs.getBigDecimal("revenue")), rs.getLong("n")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkshopBookingHour> workshopBookings(Collection<Long> locationIds, Instant from, Instant to) {
        if (locationIds.isEmpty()) return List.of();
        return jdbc.query(WORKSHOP_BOOKINGS, params(locationIds, from, to), (rs, row) -> new WorkshopBookingHour(
            rs.getLong("h"), rs.getLong("booked"), rs.getLong("confirmed"), rs.getLong("cancelled"), rs.getLong("abandoned")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceHour> attendance(Collection<Long> locationIds, Instant from, Instant to) {
        if (locationIds.isEmpty()) return List.of();
        return jdbc.query(ATTENDANCE, params(locationIds, from, to), (rs, row) -> new AttendanceHour(
            rs.getLong("h"), rs.getLong("registered"), rs.getLong("checked_in"), rs.getLong("no_show"), rs.getLong("cancelled")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevenueHour> workshopRevenue(Collection<Long> locationIds, Instant from, Instant to) {
        if (locationIds.isEmpty()) return List.of();
        return jdbc.query(WORKSHOP_REVENUE, params(locationIds, from, to), (rs, row) -> new RevenueHour(
            rs.getLong("h"), whole(rs.getBigDecimal("revenue")), rs.getLong("n")));
    }

    private static MapSqlParameterSource params(Collection<Long> ids, Instant from, Instant to) {
        return new MapSqlParameterSource("ids", ids).addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to));
    }

    private static BigInteger whole(BigDecimal amount) {
        return amount == null ? BigInteger.ZERO : amount.setScale(0, RoundingMode.DOWN).toBigIntegerExact();
    }
}
