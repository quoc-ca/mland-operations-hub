package edu.fpt.sep490_g22.ppswbs_backend.features.reporting;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ReportingPeriodResolver;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportingPeriodResolverTest {
    @Test void resolvesMondayBasedVietnamWeek() {
        var resolver = new ReportingPeriodResolver(Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneOffset.UTC));
        var period = resolver.resolve("WEEK", LocalDate.of(2026, 10, 27));
        assertThat(period.startAt()).isEqualTo(Instant.parse("2026-10-25T17:00:00Z"));
        assertThat(period.endAt()).isEqualTo(Instant.parse("2026-10-27T03:00:00Z"));
    }

    @Test void resolvesCalendarBoundariesAndTruncatesCurrentPeriod() {
        var resolver = new ReportingPeriodResolver(Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneOffset.UTC));
        assertThat(resolver.resolve(null, null).startAt()).isEqualTo(Instant.parse("2026-09-30T17:00:00Z"));
        assertThat(resolver.resolve(null, null).endAt()).isEqualTo(Instant.parse("2026-10-27T03:00:00Z"));
        var day = resolver.resolve("DAY", LocalDate.of(2026, 10, 26));
        assertThat(day.startAt()).isEqualTo(Instant.parse("2026-10-25T17:00:00Z"));
        assertThat(day.endAt()).isEqualTo(Instant.parse("2026-10-26T17:00:00Z"));
        var year = resolver.resolve("YEAR", LocalDate.of(2025, 5, 1));
        assertThat(year.startAt()).isEqualTo(Instant.parse("2024-12-31T17:00:00Z"));
        assertThat(year.endAt()).isEqualTo(Instant.parse("2025-12-31T17:00:00Z"));
        assertThatThrownBy(() -> resolver.resolve("DAY", LocalDate.of(2026, 10, 28))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> resolver.resolve("CUSTOM", null)).isInstanceOf(RuntimeException.class);
    }
}
