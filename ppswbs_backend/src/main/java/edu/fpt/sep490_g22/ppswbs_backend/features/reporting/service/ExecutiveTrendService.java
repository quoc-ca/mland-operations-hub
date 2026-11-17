package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveReportDtos.ReportScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveTrendDtos.ExecutiveTrendResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveTrendDtos.TrendPoint;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveTrendDtos.TrendValues;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort.AttendanceHour;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort.RetailOrderHour;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort.RevenueHour;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort.WorkshopBookingHour;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * FE-10 trends (Session 2026-11-16): for the chosen period and branches, retail orders placed / paid / cancelled /
 * expired and retail revenue, workshop tickets booked / confirmed / cancelled / abandoned, workshop places registered /
 * checked in / no-show / cancelled and workshop revenue, by hour (a day), by day (a week or a month) or by month (a
 * year), plus the previous period of the same length for comparison. Revenue uses the same facts as the KPIs, so the
 * buckets add up to the KPI cards. Read-only; a failing source is null, never zero.
 */
@Slf4j
@Service
public class ExecutiveTrendService {

    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH'h'");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("'T'M");

    private final ReportingTrendPort sources;
    private final ReportingPeriodResolver periods;
    private final Clock clock;

    @Autowired
    public ExecutiveTrendService(ReportingTrendPort sources, ReportingPeriodResolver periods) {
        this(sources, periods, Clock.systemUTC());
    }

    ExecutiveTrendService(ReportingTrendPort sources, ReportingPeriodResolver periods, Clock clock) {
        this.sources = sources;
        this.periods = periods;
        this.clock = clock;
    }

    public ExecutiveTrendResponse trend(String periodType, LocalDate anchorDate, List<Long> locationIds) {
        var period = periods.resolve(periodType, anchorDate);
        Instant from = period.startAt();
        Instant to = period.endAt();
        ChronoUnit unit = switch (period.periodType()) {
            case "DAY" -> ChronoUnit.HOURS;
            case "YEAR" -> ChronoUnit.MONTHS;
            default -> ChronoUnit.DAYS;
        };
        LocalDateTime start = LocalDateTime.ofInstant(from, ReportingPeriodResolver.BUSINESS_ZONE);
        List<LocalDateTime> buckets = new ArrayList<>();
        for (LocalDateTime cursor = start; zoned(cursor).toInstant().isBefore(to); cursor = cursor.plus(1, unit)) buckets.add(cursor);

        List<String> unavailable = new ArrayList<>();
        Series current = read(locationIds, from, to, unavailable);
        // The previous period has the same length, so a current period still running compares like for like.
        Instant previousFrom = zoned(start.minus(1, periodUnit(period.periodType()))).toInstant();
        Instant previousTo = previousFrom.plus(Duration.between(from, to));
        Series previous = read(locationIds, previousFrom, previousTo, new ArrayList<>());

        List<TrendPoint> points = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            LocalDateTime bucket = buckets.get(i);
            int index = i;
            points.add(new TrendPoint(zoned(bucket).toInstant().toString(), label(bucket, unit),
                current.values(hour -> bucketOf(hour, from, start, unit) == index)));
        }

        var scope = new ReportScope(period.periodType(), from.toString(), to.toString(),
            ReportingPeriodResolver.BUSINESS_ZONE.getId(), locationIds);
        return new ExecutiveTrendResponse(scope, switch (unit) { case HOURS -> "HOUR"; case MONTHS -> "MONTH"; default -> "DAY"; },
            points, current.values(hour -> true), previous.values(hour -> true), previousFrom.toString(), previousTo.toString(),
            List.copyOf(unavailable), Instant.now(clock).toString());
    }

    private Series read(List<Long> ids, Instant from, Instant to, List<String> unavailable) {
        return new Series(
            read("RETAIL_ORDERS", () -> sources.retailOrders(ids, from, to), unavailable),
            read("RETAIL_REVENUE", () -> sources.retailRevenue(ids, from, to), unavailable),
            read("WORKSHOP_BOOKINGS", () -> sources.workshopBookings(ids, from, to), unavailable),
            read("ATTENDANCE", () -> sources.attendance(ids, from, to), unavailable),
            read("WORKSHOP_REVENUE", () -> sources.workshopRevenue(ids, from, to), unavailable));
    }

    /** The bucket an hour offset falls in, or -1 outside the period. */
    static int bucketOf(long hour, Instant from, LocalDateTime start, ChronoUnit unit) {
        LocalDateTime at = LocalDateTime.ofInstant(from.plus(hour, ChronoUnit.HOURS), ReportingPeriodResolver.BUSINESS_ZONE);
        long index = switch (unit) {
            case HOURS -> ChronoUnit.HOURS.between(start, at);
            case MONTHS -> ChronoUnit.MONTHS.between(start.toLocalDate().withDayOfMonth(1), at.toLocalDate().withDayOfMonth(1));
            default -> ChronoUnit.DAYS.between(start.toLocalDate(), at.toLocalDate());
        };
        return index < 0 || index > Integer.MAX_VALUE ? -1 : (int) index;
    }

    private static ChronoUnit periodUnit(String periodType) {
        return switch (periodType) {
            case "DAY" -> ChronoUnit.DAYS;
            case "WEEK" -> ChronoUnit.WEEKS;
            case "YEAR" -> ChronoUnit.YEARS;
            default -> ChronoUnit.MONTHS;
        };
    }

    private static String label(LocalDateTime bucket, ChronoUnit unit) {
        return switch (unit) {
            case HOURS -> HOUR_LABEL.format(bucket);
            case MONTHS -> MONTH_LABEL.format(bucket);
            default -> DAY_LABEL.format(bucket);
        };
    }

    private static ZonedDateTime zoned(LocalDateTime local) {
        return local.atZone(ReportingPeriodResolver.BUSINESS_ZONE);
    }

    private static <T> List<T> read(String source, Supplier<List<T>> query, List<String> unavailable) {
        try {
            return query.get();
        } catch (RuntimeException ex) {
            log.warn("FE-10 trend source {} unavailable: {}", source, ex.getClass().getSimpleName());
            unavailable.add(source);
            return null;
        }
    }

    private interface HourFilter { boolean test(long hour); }

    /** The five sources of one period; a null list is a source that could not be read. */
    private record Series(List<RetailOrderHour> orders, List<RevenueHour> retailRevenue, List<WorkshopBookingHour> bookings,
                          List<AttendanceHour> attendance, List<RevenueHour> workshopRevenue) {

        TrendValues values(HourFilter in) {
            long[] o = new long[4];
            if (orders != null) orders.stream().filter(r -> in.test(r.hour())).forEach(r -> {
                o[0] += r.placed(); o[1] += r.paid(); o[2] += r.cancelled(); o[3] += r.expired(); });
            long[] b = new long[4];
            if (bookings != null) bookings.stream().filter(r -> in.test(r.hour())).forEach(r -> {
                b[0] += r.booked(); b[1] += r.confirmed(); b[2] += r.cancelled(); b[3] += r.abandoned(); });
            long[] a = new long[4];
            if (attendance != null) attendance.stream().filter(r -> in.test(r.hour())).forEach(r -> {
                a[0] += r.registered(); a[1] += r.checkedIn(); a[2] += r.noShow(); a[3] += r.cancelled(); });
            return new TrendValues(
                orders == null ? null : o[0], orders == null ? null : o[1], orders == null ? null : o[2], orders == null ? null : o[3],
                money(retailRevenue, in),
                bookings == null ? null : b[0], bookings == null ? null : b[1], bookings == null ? null : b[2], bookings == null ? null : b[3],
                attendance == null ? null : a[0], attendance == null ? null : a[1], attendance == null ? null : a[2], attendance == null ? null : a[3],
                money(workshopRevenue, in));
        }

        private static String money(List<RevenueHour> rows, HourFilter in) {
            if (rows == null) return null;
            return ReportingMoney.vnd(rows.stream().filter(r -> in.test(r.hour())).map(RevenueHour::revenue)
                .reduce(BigInteger.ZERO, BigInteger::add));
        }
    }
}
