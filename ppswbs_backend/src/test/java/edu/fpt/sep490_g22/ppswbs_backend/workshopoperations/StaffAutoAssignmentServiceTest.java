package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountId;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.InternalRosterEntry;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.RosterOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionStaffAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.StaffAutoAssignmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Staff are assigned by default to the sessions of their location")
class StaffAutoAssignmentServiceTest {

    private final AccountFacade accounts = mock(AccountFacade.class);
    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final SessionStaffAssignmentRepository assignments = mock(SessionStaffAssignmentRepository.class);
    private final StaffAutoAssignmentService service = new StaffAutoAssignmentService(
            accounts, sessions, assignments, mock(OperationsAuditEventRepository.class));

    private static WorkshopSession session(long id, long locationId, SessionState state) {
        WorkshopLocation location = mock(WorkshopLocation.class);
        when(location.getId()).thenReturn(locationId);
        WorkshopSession session = mock(WorkshopSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getLocation()).thenReturn(location);
        when(session.getState()).thenReturn(state);
        return session;
    }

    private void roster(long locationId, Long... staffIds) {
        List<InternalRosterEntry> entries = java.util.Arrays.stream(staffIds)
                .map(id -> new InternalRosterEntry(AccountId.of(id), "Staff " + id, AccountRole.STAFF)).toList();
        when(accounts.findEligibleRoster(locationId)).thenReturn(RosterOutcome.available(entries));
    }

    private static SessionStaffAssignment row(WorkshopSession session, long accountId, boolean active) {
        return SessionStaffAssignment.builder().session(session).accountId(accountId).assignedById(1L).active(active).build();
    }

    @Test
    @DisplayName("a new session gets every eligible Staff of its location, assigned by the creating Manager")
    void newSessionGetsTheRoster() {
        WorkshopSession created = session(10, 1, SessionState.PUBLISHED);
        roster(1, 21L, 22L);
        when(assignments.findBySessionIdIn(any())).thenReturn(List.of());

        int added = service.assignRosterToNewSession(created, 99L);

        assertThat(added).isEqualTo(2);
        ArgumentCaptor<SessionStaffAssignment> saved = ArgumentCaptor.forClass(SessionStaffAssignment.class);
        verify(assignments, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(SessionStaffAssignment::getAccountId).containsExactly(21L, 22L);
        assertThat(saved.getAllValues()).allMatch(item -> item.isActive() && item.getAssignedById().equals(99L));
    }

    @Test
    @DisplayName("a closed or cancelled session is not assigned")
    void nonAssignableStateIsSkipped() {
        roster(1, 21L);

        assertThat(service.assignRosterToNewSession(session(10, 1, SessionState.CANCELLED), 99L)).isZero();
        verify(assignments, never()).save(any());
    }

    @Test
    @DisplayName("the sync adds a missing Staff member but never re-adds one a Manager revoked")
    void syncKeepsRevocations() {
        WorkshopSession first = session(10, 1, SessionState.PUBLISHED);
        WorkshopSession second = session(11, 1, SessionState.PUBLISHED);
        roster(1, 21L, 22L, 23L);
        when(sessions.findByWorkshopDateGreaterThanEqualAndStateIn(any(LocalDate.class), any(Collection.class)))
                .thenReturn(List.of(first, second));
        // 21 is active on both sessions, 22 was revoked on the first, 23 is new everywhere.
        when(assignments.findBySessionIdIn(any())).thenReturn(List.of(
                row(first, 21L, true), row(first, 22L, false), row(second, 21L, true)));

        int added = service.syncUpcomingSessions();

        // first: only 23; second: 22 and 23
        assertThat(added).isEqualTo(3);
        ArgumentCaptor<SessionStaffAssignment> saved = ArgumentCaptor.forClass(SessionStaffAssignment.class);
        verify(assignments, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(item -> item.getSession().getId() + ":" + item.getAccountId())
                .containsExactlyInAnyOrder("10:23", "11:22", "11:23");
    }

    @Test
    @DisplayName("a location whose roster is unavailable adds nothing")
    void unavailableRosterAddsNothing() {
        when(accounts.findEligibleRoster(1L)).thenReturn(RosterOutcome.unavailable());

        assertThat(service.assignRosterToNewSession(session(10, 1, SessionState.PUBLISHED), 99L)).isZero();
        verify(assignments, never()).save(any());
    }
}
