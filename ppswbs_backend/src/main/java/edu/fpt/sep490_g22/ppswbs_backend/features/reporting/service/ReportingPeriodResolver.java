package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class ReportingPeriodResolver {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Clock clock;
    public ReportingPeriodResolver() { this(Clock.systemUTC()); }
    public ReportingPeriodResolver(Clock clock) { this.clock = clock; }
    public ReportingPeriod resolve(String periodType, LocalDate anchorDate) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(BUSINESS_ZONE);
        String type = periodType == null ? "MONTH" : periodType.toUpperCase();
        LocalDate anchor = anchorDate == null ? now.toLocalDate() : anchorDate;
        LocalDate start = switch (type) {
            case "DAY" -> anchor;
            case "WEEK" -> anchor.with(DayOfWeek.MONDAY);
            case "MONTH" -> anchor.withDayOfMonth(1);
            case "YEAR" -> anchor.withDayOfYear(1);
            default -> throw new ReportingConflictException("periodType must be DAY, WEEK, MONTH, or YEAR");
        };
        LocalDate next = switch (type) {
            case "DAY" -> start.plusDays(1);
            case "WEEK" -> start.plusWeeks(1);
            case "MONTH" -> start.plusMonths(1);
            default -> start.plusYears(1);
        };
        Instant startAt = start.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant endAt = next.atStartOfDay(BUSINESS_ZONE).toInstant();
        if (!startAt.isBefore(now.toInstant())) throw new ReportingConflictException("Reporting period cannot be future-only");
        return new ReportingPeriod(type, startAt, endAt.isAfter(now.toInstant()) ? now.toInstant() : endAt);
    }
    public record ReportingPeriod(String periodType, Instant startAt, Instant endAt) { }
}
