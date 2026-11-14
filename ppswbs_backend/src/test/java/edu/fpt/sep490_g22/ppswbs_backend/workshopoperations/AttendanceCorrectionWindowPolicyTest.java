package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceCorrectionWindowPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceCorrectionWindowPolicyTest {
    private static final Instant FIRST = Instant.parse("2026-10-30T10:00:00Z");
    private static final Instant LAST = Instant.parse("2026-10-31T09:00:00Z");

    @Test
    void deadlineUsesLatestRequiredSessionFinalizationAndAllowsCorrectionUntilExclusiveBoundary() {
        List<Instant> finalizationTimes = List.of(FIRST, LAST);
        Instant deadline = LAST.plusSeconds(24 * 60 * 60);

        assertThat(AttendanceCorrectionWindowPolicy.closesAt(finalizationTimes)).contains(deadline);
        assertThat(AttendanceCorrectionWindowPolicy.isClosed(finalizationTimes, deadline.minusNanos(1))).isFalse();
        assertThat(AttendanceCorrectionWindowPolicy.isClosed(finalizationTimes, deadline)).isTrue();
    }

    @Test
    void missingFinalizationEvidenceNeverReportsCorrectionWindowClosed() {
        List<Instant> incomplete = java.util.Arrays.asList(FIRST, null);
        assertThat(AttendanceCorrectionWindowPolicy.closesAt(incomplete)).isEmpty();
        assertThat(AttendanceCorrectionWindowPolicy.isClosed(incomplete, Instant.MAX)).isFalse();
    }
}
