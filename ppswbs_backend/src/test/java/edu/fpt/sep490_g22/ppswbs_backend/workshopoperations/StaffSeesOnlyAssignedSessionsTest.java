package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionStaffAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CapacityHoldService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLocationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOtaSnapshotService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSlotService;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Owner decision 2026-11-06: a Staff member sees and opens only the sessions they are assigned to. */
class StaffSeesOnlyAssignedSessionsTest {
    private static final Long LOCATION = 4L;
    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final WorkshopOperationsAuthorizationService authorization = mock(WorkshopOperationsAuthorizationService.class);
    private final AccountFacade accounts = mock(AccountFacade.class);
    private final WorkshopLocationService locations = mock(WorkshopLocationService.class);
    private final SessionStaffAssignmentRepository assignments = mock(SessionStaffAssignmentRepository.class);
    private final WorkshopSessionService service = new WorkshopSessionService(sessions, locations, mock(WorkshopSlotService.class),
            authorization, accounts, mock(OperationsAuditEventRepository.class), mock(CapacityHoldService.class),
            mock(WorkshopOtaSnapshotService.class), null, assignments);

    private WorkshopSession session(long id, String reference) {
        WorkshopSession session = mock(WorkshopSession.class, Answers.RETURNS_DEEP_STUBS);
        when(session.getId()).thenReturn(id);
        when(session.getReference()).thenReturn(reference);
        when(session.getLocation().getId()).thenReturn(LOCATION);
        when(session.getSlot().getReference()).thenReturn("slot");
        return session;
    }

    private AccountSummary account(ActorRole role) {
        return new AccountSummary(33L, role, ActorStatus.ACTIVE, "someone@mland.test", Instant.now(), Instant.now());
    }

    @Test
    void aStaffListsOnlyTheirAssignedSessionsWhileAManagerListsAll() {
        WorkshopSession mine = session(1L, "S-1");
        WorkshopSession other = session(2L, "S-2");
        // Built first: creating a mock inside a stubbing call is not allowed.
        List<WorkshopSession> all = List.of(mine, other);
        when(sessions.findAll()).thenReturn(all);

        AccountSummary staff = account(ActorRole.STAFF);
        when(authorization.authenticate("Bearer staff")).thenReturn(staff);
        when(authorization.isGlobalWorkshopActor(staff)).thenReturn(false);
        when(accounts.getLocationScope(33L)).thenReturn(List.of(LOCATION));
        SessionStaffAssignment assignment = SessionStaffAssignment.builder().session(mine).accountId(33L).assignedById(10L).active(true).build();
        when(assignments.findByAccountIdAndActiveTrue(33L)).thenReturn(List.of(assignment));

        assertThat(service.searchSessions("Bearer staff", null, null, null, null)).extracting(SessionResponse::getId).containsExactly("S-1");

        AccountSummary manager = account(ActorRole.MANAGER);
        when(authorization.authenticate("Bearer manager")).thenReturn(manager);
        when(authorization.isGlobalWorkshopActor(manager)).thenReturn(true);
        when(authorization.isManager(manager)).thenReturn(true);

        assertThat(service.searchSessions("Bearer manager", null, null, null, null)).extracting(SessionResponse::getId).containsExactlyInAnyOrder("S-1", "S-2");
    }

    @Test
    void aStaffCannotOpenASessionTheyAreNotAssignedTo() {
        WorkshopSession notMine = session(2L, "S-2");
        when(sessions.findByReference("S-2")).thenReturn(Optional.of(notMine));
        AccountSummary staff = account(ActorRole.STAFF);
        when(authorization.authenticate("Bearer staff")).thenReturn(staff);
        when(authorization.isGlobalWorkshopActor(staff)).thenReturn(false);
        doThrow(new WorkshopForbiddenException("not assigned")).when(authorization).requireAssignment(staff, 2L);

        assertThatThrownBy(() -> service.getSessionByReference("Bearer staff", "S-2")).isInstanceOf(WorkshopForbiddenException.class);
    }

    @Test
    void aManagerOpensAnySessionInScope() {
        WorkshopSession any = session(2L, "S-2");
        when(sessions.findByReference("S-2")).thenReturn(Optional.of(any));
        AccountSummary manager = account(ActorRole.MANAGER);
        when(authorization.authenticate("Bearer manager")).thenReturn(manager);
        when(authorization.isGlobalWorkshopActor(manager)).thenReturn(true);

        assertThat(service.getSessionByReference("Bearer manager", "S-2").getId()).isEqualTo("S-2");
        verify(authorization, never()).requireAssignment(any(), any());
    }
}
