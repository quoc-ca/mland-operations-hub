package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort.AttendanceHour;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort.RetailOrderHour;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort.RevenueHour;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort.WorkshopBookingHour;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutiveTrendServiceTest {
    // 2026-11-14 10:00 in Vietnam.
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC);
    private static final Instant NOV_1 = Instant.parse("2026-10-31T17:00:00Z");
    private final ReportingTrendPort sources = mock(ReportingTrendPort.class);
    private final ExecutiveTrendService service = new ExecutiveTrendService(sources, new ReportingPeriodResolver(NOW), NOW);

    @Test void aMonthIsBucketedByVietnamDayUpToNow() {
        // Hour 0 = 1 Nov 00:00 VN; hour 30 = 2 Nov 06:00 VN; hour 300 = 13 Nov 12:00 VN.
        when(sources.retailOrders(any(), eq(NOV_1), any())).thenReturn(List.of(
            new RetailOrderHour(0, 3, 2, 1, 0), new RetailOrderHour(30, 4, 1, 0, 2)));
        when(sources.retailRevenue(any(), eq(NOV_1), any())).thenReturn(List.of(
            new RevenueHour(0, BigInteger.valueOf(500_000), 2), new RevenueHour(300, BigInteger.valueOf(250_000), 1)));
        when(sources.workshopBookings(any(), eq(NOV_1), any())).thenReturn(List.of(new WorkshopBookingHour(30, 5, 3, 1, 1)));
        when(sources.attendance(any(), eq(NOV_1), any())).thenReturn(List.of(new AttendanceHour(300, 10, 7, 2, 1)));
        when(sources.workshopRevenue(any(), eq(NOV_1), any())).thenReturn(List.of());

        var trend = service.trend(null, null, List.of(1L));

        assertThat(trend.granularity()).isEqualTo("DAY");
        assertThat(trend.points()).hasSize(14);
        assertThat(trend.points().get(0).label()).isEqualTo("01/11");
        assertThat(trend.points().get(0).startAt()).isEqualTo("2026-10-31T17:00:00Z");
        assertThat(trend.points().get(0).values().retailPlaced()).isEqualTo(3L);
        assertThat(trend.points().get(0).values().retailRevenue()).isEqualTo("500000");
        assertThat(trend.points().get(1).values().retailExpired()).isEqualTo(2L);
        assertThat(trend.points().get(1).values().workshopAbandoned()).isEqualTo(1L);
        assertThat(trend.points().get(12).values().placesCheckedIn()).isEqualTo(7L);
        assertThat(trend.points().get(12).values().placesNoShow()).isEqualTo(2L);
        assertThat(trend.points().get(5).values().retailPlaced()).isZero();
        assertThat(trend.current().retailPlaced()).isEqualTo(7L);
        assertThat(trend.current().retailRevenue()).isEqualTo("750000");
        assertThat(trend.current().workshopRevenue()).isEqualTo("0");
        assertThat(trend.unavailable()).isEmpty();
    }

    @Test void theDayIsByHourAndTheYearByMonth() {
        when(sources.retailOrders(any(), any(), any())).thenReturn(List.of(new RetailOrderHour(9, 2, 2, 0, 0)));

        var day = service.trend("DAY", LocalDate.parse("2026-11-13"), List.of(1L));
        assertThat(day.granularity()).isEqualTo("HOUR");
        assertThat(day.points()).hasSize(24);
        assertThat(day.points().get(9).label()).isEqualTo("09h");
        assertThat(day.points().get(9).values().retailPaid()).isEqualTo(2L);

        var year = service.trend("YEAR", null, List.of(1L));
        assertThat(year.granularity()).isEqualTo("MONTH");
        assertThat(year.points()).hasSize(11);
        assertThat(year.points().get(10).label()).isEqualTo("T11");
    }

    @Test void comparesWithThePreviousPeriodOfTheSameLength() {
        Instant oct1 = Instant.parse("2026-09-30T17:00:00Z");
        when(sources.retailOrders(any(), any(), any())).thenReturn(List.of());
        when(sources.retailOrders(any(), eq(oct1), any())).thenReturn(List.of(new RetailOrderHour(5, 9, 6, 2, 1)));

        var trend = service.trend("MONTH", null, List.of(1L));

        assertThat(trend.previousStartAt()).isEqualTo("2026-09-30T17:00:00Z");
        // 1-14 Nov 10:00 is 13 days 10 hours; so is 1-14 Oct 10:00.
        assertThat(trend.previousEndAt()).isEqualTo("2026-10-14T03:00:00Z");
        assertThat(trend.previous().retailPlaced()).isEqualTo(9L);
        assertThat(trend.current().retailPlaced()).isZero();
    }

    @Test void aFailingSourceIsNullAndNamedNeverZero() {
        when(sources.retailOrders(any(), any(), any())).thenReturn(List.of(new RetailOrderHour(0, 1, 1, 0, 0)));
        when(sources.attendance(any(), any(), any())).thenThrow(new IllegalStateException("db down"));

        var trend = service.trend("WEEK", LocalDate.parse("2026-11-12"), List.of(1L));

        assertThat(trend.points()).hasSize(6); // Mon 9 to Sat 14 Nov
        assertThat(trend.unavailable()).containsExactly("ATTENDANCE");
        assertThat(trend.current().placesRegistered()).isNull();
        assertThat(trend.points().get(0).values().placesCheckedIn()).isNull();
        assertThat(trend.current().retailPlaced()).isEqualTo(1L);
    }
}
