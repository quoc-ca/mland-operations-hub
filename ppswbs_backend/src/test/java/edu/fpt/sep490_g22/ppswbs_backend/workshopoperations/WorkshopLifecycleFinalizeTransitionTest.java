package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StateTransitionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOtaSnapshotService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopResponsibilityService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkshopLifecycleFinalizeTransitionTest {
    private final AccountFacade accounts = mock(AccountFacade.class);
    private final WorkshopOperationsAuthorizationService authorization = mock(WorkshopOperationsAuthorizationService.class);
    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final WorkshopLifecycleService service = new WorkshopLifecycleService(accounts, authorization, sessions,
            mock(WorkshopResponsibilityService.class), mock(OperationsAuditEventRepository.class), mock(WorkshopOtaSnapshotService.class));

    /** The plain transition would skip marking no-shows and the finalization time, so only the dedicated action may finalize. */
    @Test
    void aPlainStateTransitionToAttendanceFinalizedIsNotSupportedAndChangesNothing() {
        AccountSummary manager = new AccountSummary(1L, ActorRole.MANAGER, ActorStatus.ACTIVE, "m@example.test", Instant.now(), Instant.now());
        WorkshopLocation location = mock(WorkshopLocation.class);
        when(location.getId()).thenReturn(4L);
        WorkshopSession session = mock(WorkshopSession.class);
        when(session.getLocation()).thenReturn(location);
        when(session.getState()).thenReturn(SessionState.IN_PROGRESS);
        when(authorization.authenticate("Bearer t")).thenReturn(manager);
        when(authorization.isGlobalWorkshopActor(manager)).thenReturn(true);
        when(accounts.getAccountStatus(1L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(sessions.findByReference("S-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.transitionState("Bearer t", null, "S-1",
                StateTransitionRequest.builder().targetState(SessionState.ATTENDANCE_FINALIZED).build()))
                .isInstanceOf(WorkshopLifecycleException.class)
                .hasMessageContaining("attendance-finalizations");

        verify(session, never()).setState(any());
        verify(sessions, never()).save(any());
    }
}
