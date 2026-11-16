package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Read-only trend aggregates for FE-10 (Session 2026-11-16). Every query aggregates in MySQL into whole hours counted
 * from {@code from}, so no order, ticket or customer row leaves the source; the service folds the hours into hour,
 * day or month buckets in Vietnam time. One call per source, so one failing source never hides the others. Every
 * period is the half-open interval {@code [from, to)}.
 */
public interface ReportingTrendPort {

    /** FE-08 branch orders by the time they were placed, with what became of them (paid, cancelled, expired). */
    List<RetailOrderHour> retailOrders(Collection<Long> locationIds, Instant from, Instant to);

    /** FE-08 VND revenue (product value after discount, no shipping) by the time the order became PAID. */
    List<RevenueHour> retailRevenue(Collection<Long> locationIds, Instant from, Instant to);

    /** FE-03 tickets by the time their booking group was placed, with what became of them. */
    List<WorkshopBookingHour> workshopBookings(Collection<Long> locationIds, Instant from, Instant to);

    /** FE-03 participant places (ticket x session) by the scheduled start of the session, with their attendance. */
    List<AttendanceHour> attendance(Collection<Long> locationIds, Instant from, Instant to);

    /** FE-03 VND revenue of finalized (fully paid) folios by the time they became fully paid. */
    List<RevenueHour> workshopRevenue(Collection<Long> locationIds, Instant from, Instant to);

    record RetailOrderHour(long hour, long placed, long paid, long cancelled, long expired) { }

    record RevenueHour(long hour, BigInteger revenue, long count) { }

    record WorkshopBookingHour(long hour, long booked, long confirmed, long cancelled, long abandoned) { }

    record AttendanceHour(long hour, long registered, long checkedIn, long noShow, long cancelled) { }
}
