package edu.fpt.sep490_g22.ppswbs_backend.features.reporting;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ReportingPeriodResolver;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;

class ReportingPeriodResolverTest {
    @Test void resolvesMondayBasedVietnamWeek() {
        var resolver = new ReportingPeriodResolver(Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneOffset.UTC));
        var period = resolver.resolve("WEEK", LocalDate.of(2026, 10, 27));
        assertThat(period.startAt()).isEqualTo(Instant.parse("2026-10-25T17:00:00Z"));
        assertThat(period.endAt()).isEqualTo(Instant.parse("2026-10-27T03:00:00Z"));
    }
}
