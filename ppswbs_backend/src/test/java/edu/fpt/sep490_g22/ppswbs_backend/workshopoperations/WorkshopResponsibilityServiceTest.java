package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountId;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.InternalRosterEntry;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.RosterOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AssignableStaffResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopDependencyUnavailableException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.TargetAccountSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StaffAssignmentRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionStaffAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLastStaffAssignmentException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLocationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopResponsibilityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopStaffNotEligibleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopStaffNotEligibleException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 003.2 T115 (last-assignment guard, missingStaff), T124 (no operational lead), T130 (assignment eligibility). */
class WorkshopResponsibilityServiceTest {

    private static final String AUTH = "Bearer test";
    private static final long SESSION_ID = 7L;
    private static final long LOCATION_ID = 4L;

    private final WorkshopOperationsAuthorizationService authorization = mock(WorkshopOperationsAuthorizationService.class);
    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final SessionStaffAssignmentRepository assignments = mock(SessionStaffAssignmentRepository.class);
    private final OperationsAuditEventRepository audits = mock(OperationsAuditEventRepository.class);
    private final WorkshopResponsibilityService service = new WorkshopResponsibilityService(
            authorization, sessions, assignments, audits, mock(WorkshopLocationService.class));

    private final AccountSummary manager = new AccountSummary(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, "m@x.test", Instant.now(), Instant.now());
    private final AccountSummary admin = new AccountSummary(99L, ActorRole.ADMIN, ActorStatus.ACTIVE, "a@x.test", Instant.now(), Instant.now());
    private WorkshopSession session;

    @BeforeEach
    void setUp() {
        session = session(SessionState.IN_PROGRESS, Instant.now().minusSeconds(60));
        when(sessions.findByReference("S-1")).thenReturn(Optional.of(session));
        when(authorization.authenticate(AUTH)).thenReturn(manager);
        when(authorization.isManager(manager)).thenReturn(true);
    }

    private WorkshopSession session(SessionState state, Instant checkInOpensAt) {
        WorkshopSession s = mock(WorkshopSession.class, Answers.RETURNS_DEEP_STUBS);
        when(s.getId()).thenReturn(SESSION_ID);
        when(s.getReference()).thenReturn("S-1");
        when(s.getState()).thenReturn(state);
        when(s.getCheckInOpensAt()).thenReturn(checkInOpensAt);
        when(s.getLocation().getId()).thenReturn(LOCATION_ID);
        when(s.getSlot().getReference()).thenReturn("slot");
        return s;
    }

    private void useSession(SessionState state, Instant checkInOpensAt) {
        WorkshopSession s = session(state, checkInOpensAt);
        when(sessions.findByReference("S-1")).thenReturn(Optional.of(s));
    }

    private SessionStaffAssignment assignment(long accountId) {
        return SessionStaffAssignment.builder().session(session).accountId(accountId).active(true).assignedAt(Instant.now()).build();
    }

    private void assigned(long... accountIds) {
        List<SessionStaffAssignment> list = java.util.Arrays.stream(accountIds).mapToObj(this::assignment).toList();
        when(assignments.findBySessionIdAndActiveTrue(SESSION_ID)).thenReturn(list);
        for (SessionStaffAssignment a : list) {
            when(assignments.findBySessionIdAndAccountIdAndActiveTrue(SESSION_ID, a.getAccountId())).thenReturn(Optional.of(a));
        }
    }

    private void target(long id, ActorRole role, ActorStatus status, Long... locations) {
        when(authorization.getTargetAccount(id)).thenReturn(Optional.of(new TargetAccountSnapshot(id, role, status, List.of(locations))));
    }

    private List<OperationsAuditEvent> savedAudits(int times) {
        ArgumentCaptor<OperationsAuditEvent> captor = ArgumentCaptor.forClass(OperationsAuditEvent.class);
        verify(audits, org.mockito.Mockito.times(times)).save(captor.capture());
        return captor.getAllValues();
    }

    // ---- T130 -------------------------------------------------------------------------------------------------

    @Test
    void unknownAccountIsAccountNotFound() {
        when(authorization.getTargetAccount(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignStaff(AUTH, null, "S-1", request(55L)))
                .isInstanceOfSatisfying(WorkshopStaffNotEligibleException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.ACCOUNT_NOT_FOUND));
    }

    @Test
    void nonStaffRoleIsNotStaff() {
        target(55L, ActorRole.MANAGER, ActorStatus.ACTIVE, LOCATION_ID);

        assertThatThrownBy(() -> service.assignStaff(AUTH, null, "S-1", request(55L)))
                .isInstanceOfSatisfying(WorkshopStaffNotEligibleException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.NOT_STAFF));
    }

    @Test
    void inactiveStaffIsNotActive() {
        target(55L, ActorRole.STAFF, ActorStatus.SUSPENDED, LOCATION_ID);

        assertThatThrownBy(() -> service.assignStaff(AUTH, null, "S-1", request(55L)))
                .isInstanceOfSatisfying(WorkshopStaffNotEligibleException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.NOT_ACTIVE));
    }

    @Test
    void staffOutsideTheSessionLocationIsLocationNotInScope() {
        target(55L, ActorRole.STAFF, ActorStatus.ACTIVE, 99L);

        assertThatThrownBy(() -> service.assignStaff(AUTH, null, "S-1", request(55L)))
                .isInstanceOfSatisfying(WorkshopStaffNotEligibleException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.LOCATION_NOT_IN_SCOPE));
        verify(assignments, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = SessionState.class, names = {"CANCELLATION_REQUESTED", "CANCELLED", "ATTENDANCE_FINALIZED"})
    void assignmentIsRefusedInTerminalStatesWithInvalidState(SessionState state) {
        useSession(state, Instant.now().minusSeconds(60));
        target(55L, ActorRole.STAFF, ActorStatus.ACTIVE, LOCATION_ID);

        assertThatThrownBy(() -> service.assignStaff(AUTH, null, "S-1", request(55L)))
                .isInstanceOf(WorkshopLifecycleException.class);
        verify(assignments, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = SessionState.class, names = {"DRAFT", "PUBLISHED", "SALES_PAUSED", "IN_PROGRESS"})
    void eligibleStaffIsAssignedInEveryAssignableState(SessionState state) {
        useSession(state, Instant.now().plusSeconds(3600));
        target(55L, ActorRole.STAFF, ActorStatus.ACTIVE, LOCATION_ID);
        when(assignments.findBySessionIdAndAccountIdAndActiveTrue(SESSION_ID, 55L)).thenReturn(Optional.empty());

        SessionResponse response = service.assignStaff(AUTH, null, "S-1", request(55L));

        verify(assignments).save(any(SessionStaffAssignment.class));
        assertThat(response.getMissingStaff()).isNotNull();
    }

    @Test
    void duplicateAssignmentIsANoOpWithoutASecondRow() {
        target(55L, ActorRole.STAFF, ActorStatus.ACTIVE, LOCATION_ID);
        when(assignments.findBySessionIdAndAccountIdAndActiveTrue(SESSION_ID, 55L)).thenReturn(Optional.of(assignment(55L)));

        service.assignStaff(AUTH, null, "S-1", request(55L));

        verify(assignments, never()).save(any());
    }

    @Test
    void anAdminOrAnyNonManagerCannotAssign() {
        when(authorization.authenticate(AUTH)).thenReturn(admin);
        when(authorization.isManager(admin)).thenReturn(false);

        assertThatThrownBy(() -> service.assignStaff(AUTH, null, "S-1", request(55L)))
                .isInstanceOf(WorkshopForbiddenException.class);
        assertThatThrownBy(() -> service.removeStaff(AUTH, null, "S-1", 55L, true))
                .isInstanceOf(WorkshopForbiddenException.class);
        verify(assignments, never()).save(any());
    }

    // ---- T115 -------------------------------------------------------------------------------------------------

    @Test
    void lastAssignmentAfterCheckInOpensIsRefusedAndTheRefusalIsAudited() {
        assigned(33L);
        SessionStaffAssignment last = assignments.findBySessionIdAndAccountIdAndActiveTrue(SESSION_ID, 33L).orElseThrow();

        assertThatThrownBy(() -> service.removeStaff(AUTH, "corr-1", "S-1", 33L, false))
                .isInstanceOf(WorkshopLastStaffAssignmentException.class);

        assertThat(last.isActive()).isTrue();
        verify(assignments, never()).save(any());
        OperationsAuditEvent denied = savedAudits(1).get(0);
        assertThat(denied.getAction()).isEqualTo("SESSION_STAFF_REMOVED");
        assertThat(denied.getOutcome()).isEqualTo("DENIED");
        assertThat(denied.getMetadata()).contains("LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED");
    }

    @Test
    void confirmedRemovalOfTheLastAssignmentSucceedsAndIsAudited() {
        assigned(33L);
        SessionStaffAssignment last = assignments.findBySessionIdAndAccountIdAndActiveTrue(SESSION_ID, 33L).orElseThrow();

        service.removeStaff(AUTH, "corr-2", "S-1", 33L, true);

        assertThat(last.isActive()).isFalse();
        assertThat(last.getRevokedAt()).isNotNull();
        OperationsAuditEvent removed = savedAudits(1).get(0);
        assertThat(removed.getOutcome()).isEqualTo("SUCCESS");
        assertThat(removed.getMetadata()).contains("lastAssignmentConfirmed");
    }

    @Test
    void removingANonLastAssignmentNeedsNoConfirmation() {
        assigned(33L, 41L);

        service.removeStaff(AUTH, null, "S-1", 33L, false);

        assertThat(assignments.findBySessionIdAndAccountIdAndActiveTrue(SESSION_ID, 33L).orElseThrow().isActive()).isFalse();
    }

    @Test
    void beforeCheckInOpensTheLastAssignmentGoesWithoutConfirmation() {
        useSession(SessionState.PUBLISHED, Instant.now().plusSeconds(3600));
        assigned(33L);

        service.removeStaff(AUTH, null, "S-1", 33L, false);

        assertThat(assignments.findBySessionIdAndAccountIdAndActiveTrue(SESSION_ID, 33L).orElseThrow().isActive()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = SessionState.class, names = {"DRAFT", "CANCELLATION_REQUESTED", "CANCELLED", "ATTENDANCE_FINALIZED"})
    void outsideTheWatchedStatesTheLastAssignmentGoesWithoutConfirmation(SessionState state) {
        useSession(state, Instant.now().minusSeconds(60));
        assigned(33L);

        service.removeStaff(AUTH, null, "S-1", 33L, false);

        assertThat(assignments.findBySessionIdAndAccountIdAndActiveTrue(SESSION_ID, 33L).orElseThrow().isActive()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = SessionState.class, names = {"PUBLISHED", "SALES_PAUSED", "IN_PROGRESS"})
    void inEveryWatchedStateTheLastAssignmentNeedsConfirmation(SessionState state) {
        useSession(state, Instant.now().minusSeconds(60));
        assigned(33L);

        assertThatThrownBy(() -> service.removeStaff(AUTH, null, "S-1", 33L, false))
                .isInstanceOf(WorkshopLastStaffAssignmentException.class);
    }

    @Test
    void removingAnAssignmentThatIsNotActiveIsAnIdempotentNoOp() {
        when(assignments.findBySessionIdAndAccountIdAndActiveTrue(SESSION_ID, 33L)).thenReturn(Optional.empty());

        service.removeStaff(AUTH, null, "S-1", 33L, false);

        verify(audits, never()).save(any());
    }

    @Test
    void missingStaffIsTrueOnlyFromCheckInOpensInAWatchedStateWithNoAssignment() {
        Instant now = Instant.now();
        WorkshopSession open = session(SessionState.PUBLISHED, now.minusSeconds(1));
        WorkshopSession notYet = session(SessionState.PUBLISHED, now.plusSeconds(3600));
        WorkshopSession draft = session(SessionState.DRAFT, now.minusSeconds(1));
        WorkshopSession cancelled = session(SessionState.CANCELLED, now.minusSeconds(1));

        assertThat(WorkshopResponsibilityService.isMissingStaff(open, false, now)).isTrue();
        assertThat(WorkshopResponsibilityService.isMissingStaff(open, true, now)).isFalse();
        assertThat(WorkshopResponsibilityService.isMissingStaff(notYet, false, now)).isFalse();
        assertThat(WorkshopResponsibilityService.isMissingStaff(draft, false, now)).isFalse();
        assertThat(WorkshopResponsibilityService.isMissingStaff(cancelled, false, now)).isFalse();
    }

    @Test
    void theSessionResponseCarriesMissingStaffBeforeAndAfterAnAssignment() {
        when(assignments.findBySessionIdAndActiveTrue(SESSION_ID)).thenReturn(List.of());
        assertThat(service.toSessionResponse(session).getMissingStaff()).isTrue();

        assigned(33L);
        assertThat(service.toSessionResponse(session).getMissingStaff()).isFalse();
    }

    private static StaffAssignmentRequest request(Long accountId) {
        return StaffAssignmentRequest.builder().accountId(accountId).build();
    }

    @Test
    void assignableStaffListsTheRosterOfTheSessionLocationByName() {
        when(authorization.findEligibleRoster(LOCATION_ID)).thenReturn(RosterOutcome.available(List.of(
                new InternalRosterEntry(AccountId.of(41L), "no-name", null),
                new InternalRosterEntry(AccountId.of(33L), "An", null))));
        when(authorization.findAccountFullName(41L)).thenReturn(Optional.empty());
        when(authorization.findAccountFullName(33L)).thenReturn(Optional.of("An Tran"));

        assertThat(service.assignableStaff(AUTH, "S-1")).containsExactly(
                new AssignableStaffResponse(33L, "An Tran"), new AssignableStaffResponse(41L, null));
    }

    @Test
    void assignableStaffIsRefusedToANonManager() {
        when(authorization.authenticate(AUTH)).thenReturn(admin);

        assertThatThrownBy(() -> service.assignableStaff(AUTH, "S-1")).isInstanceOf(WorkshopForbiddenException.class);
        verify(authorization, never()).findEligibleRoster(any());
    }

    @Test
    void assignableStaffFailsClosedWhenTheRosterIsUnavailable() {
        when(authorization.findEligibleRoster(LOCATION_ID)).thenReturn(RosterOutcome.unavailable());

        assertThatThrownBy(() -> service.assignableStaff(AUTH, "S-1"))
                .isInstanceOf(WorkshopDependencyUnavailableException.class);
    }
}
