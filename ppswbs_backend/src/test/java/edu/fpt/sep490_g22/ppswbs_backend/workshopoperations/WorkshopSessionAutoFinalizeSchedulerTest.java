package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceFinalizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionAutoFinalizeScheduler;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** FR-059: the switch, the delay and the isolation between sessions, without a database. */
class WorkshopSessionAutoFinalizeSchedulerTest {

    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final AttendanceFinalizationService finalization = mock(AttendanceFinalizationService.class);

    private WorkshopSessionAutoFinalizeScheduler scheduler(boolean enabled) {
        return new WorkshopSessionAutoFinalizeScheduler(sessions, finalization, enabled, Duration.ofHours(2));
    }

    @Test
    void doesNothingWhileSwitchedOff() {
        scheduler(false).finalizeOverdueSessions();

        verifyNoInteractions(sessions, finalization);
    }

    @Test
    void asksForSessionsEndedTwoHoursAgoAndContinuesAfterAFailure() {
        when(sessions.findByStateAndScheduledEndAtLessThanEqual(eq(SessionState.IN_PROGRESS), any())).thenReturn(List.of(
                WorkshopSession.builder().reference("S-1").build(), WorkshopSession.builder().reference("S-2").build()));
        when(finalization.finalizeBySystemIfStillDue(eq("S-1"), any())).thenThrow(new IllegalStateException("boom"));
        Instant before = Instant.now();

        scheduler(true).finalizeOverdueSessions();

        var endedBy = forClass(Instant.class);
        verify(finalization).finalizeBySystemIfStillDue(eq("S-2"), endedBy.capture());
        assertThat(endedBy.getValue()).isBetween(before.minus(Duration.ofHours(2)), Instant.now().minus(Duration.ofHours(2)));
    }
}
