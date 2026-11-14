package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopBookingPackagePolicySnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.GroupCapacityOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveGroupCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.GroupCapacityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOtaSnapshotService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageReservationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 003.2 FR-030: group reserve is all or none, sums demand per session across lines and holds 12 minutes. */
class GroupCapacityServiceTest {

    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final CapacityHoldRepository holds = mock(CapacityHoldRepository.class);
    private final WorkshopBookingPackagePolicySnapshotRepository snapshots =
            mock(WorkshopBookingPackagePolicySnapshotRepository.class);
    private final TicketSessionEntitlementRepository entitlements = mock(TicketSessionEntitlementRepository.class);
    private final OperationsAuditEventRepository audits = mock(OperationsAuditEventRepository.class);
    private final FacadeCommandIdempotencyManager idempotency = mock(FacadeCommandIdempotencyManager.class);
    private final WorkshopPackageReservationPolicy policy = mock(WorkshopPackageReservationPolicy.class);
    private final WorkshopOtaSnapshotService ota = mock(WorkshopOtaSnapshotService.class);
    private GroupCapacityService service;

    private final UUID sessionA = UUID.randomUUID();
    private final UUID sessionB = UUID.randomUUID();
    private WorkshopSession a;
    private WorkshopSession b;

    @BeforeEach
    void setUp() {
        service = new GroupCapacityService(sessions, holds, snapshots, entitlements, audits, idempotency, policy, ota,
                new ObjectMapper().findAndRegisterModules());
        a = session(1L, sessionA, 5);
        b = session(2L, sessionB, 5);
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ClaimResult.acquired(10L, 1L));
        when(policy.validate(any())).thenReturn(null);
        when(sessions.findByReferenceInWithLock(any(Collection.class))).thenReturn(List.of(a, b));
        when(holds.sumActiveHoldsForSession(anyLong(), any())).thenReturn(0);
        when(holds.sumConfirmedHoldsForSession(anyLong())).thenReturn(0);
        when(entitlements.countActiveConfirmedSeatsForSession(anyLong())).thenReturn(0);
    }

    @Test
    void holdsEveryLineAndSessionTogetherForTwelveMinutes() {
        var command = command(line("L0", sessionA, 2), line("L1", sessionB, 3));

        Instant before = Instant.now();
        GroupCapacityOutcome outcome = service.reserveGroupCapacity(command);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(outcome.lineHoldReferences()).hasSize(2).allSatisfy(refs -> assertThat(refs).hasSize(1));
        assertThat(Duration.between(before, outcome.expiresAt()).toMinutes()).isBetween(11L, 12L);
        ArgumentCaptor<CapacityHold> saved = ArgumentCaptor.forClass(CapacityHold.class);
        verify(holds, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CapacityHold::getBookingReference).containsExactly("L0", "L1");
        assertThat(saved.getAllValues()).extracting(CapacityHold::getRequestedTicketCount).containsExactly(2, 3);
    }

    @Test
    void twoLinesOnTheSameSessionAreSummedAndTheWholeGroupIsRefused() {
        // capacity 5, 3 + 3 = 6 seats on session A: neither line can be held, nothing is saved.
        var command = command(line("L0", sessionA, 3), line("L1", sessionA, 3), line("L2", sessionB, 1));

        GroupCapacityOutcome outcome = service.reserveGroupCapacity(command);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.INSUFFICIENT_CAPACITY);
        assertThat(outcome.failingLines()).containsExactly(0, 1);
        verify(holds, never()).save(any());
    }

    @Test
    void oneShortLineRefusesTheWholeGroupAndReportsOnlyThatLine() {
        when(holds.findCountableHoldsForSessionForShare(2L)).thenReturn(java.util.List.of(
                edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHold.builder()
                        .state(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHoldState.ACTIVE)
                        .requestedTicketCount(4).expiresAt(java.time.Instant.now().plusSeconds(600)).build()));
        var command = command(line("L0", sessionA, 2), line("L1", sessionB, 2));

        GroupCapacityOutcome outcome = service.reserveGroupCapacity(command);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.failingLines()).containsExactly(1);
        verify(holds, never()).save(any());
    }

    @Test
    void sessionThatIsNotPublishedFailsItsLine() {
        b.setState(SessionState.SALES_PAUSED);
        var command = command(line("L0", sessionA, 1), line("L1", sessionB, 1));

        GroupCapacityOutcome outcome = service.reserveGroupCapacity(command);

        assertThat(outcome.reason()).isEqualTo(RejectionReason.SALE_NOT_ELIGIBLE);
        assertThat(outcome.failingLines()).containsExactly(1);
        verify(holds, never()).save(any());
    }

    @Test
    void packageRuleFailureIsReportedPerLineBeforeAnySessionIsLocked() {
        when(policy.validate(any())).thenReturn(null, RejectionReason.PACKAGE_SELECTION_INVALID);
        var command = command(line("L0", sessionA, 1), line("L1", sessionB, 1));

        GroupCapacityOutcome outcome = service.reserveGroupCapacity(command);

        assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
        assertThat(outcome.failingLines()).containsExactly(1);
        verify(sessions, never()).findByReferenceInWithLock(any());
    }

    @Test
    void duplicateLineReferenceOrZeroTicketsIsInvalid() {
        GroupCapacityOutcome duplicate = service.reserveGroupCapacity(
                command(line("L0", sessionA, 1), line("L0", sessionB, 1)));
        GroupCapacityOutcome zero = service.reserveGroupCapacity(command(line("L0", sessionA, 0)));

        assertThat(duplicate.reason()).isEqualTo(RejectionReason.INVALID_STATE);
        assertThat(duplicate.failingLines()).containsExactly(1);
        assertThat(zero.failingLines()).containsExactly(0);
        verify(holds, never()).save(any());
    }

    @Test
    void emptyGroupIsInvalid() {
        GroupCapacityOutcome outcome = service.reserveGroupCapacity(command());

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.INVALID_STATE);
    }

    @Test
    void inProgressAndMismatchClaimsDoNotTouchCapacity() {
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ClaimResult.inProgress());
        assertThat(service.reserveGroupCapacity(command(line("L0", sessionA, 1))).status())
                .isEqualTo(OutcomeStatus.IN_PROGRESS);

        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ClaimResult.mismatch());
        assertThat(service.reserveGroupCapacity(command(line("L0", sessionA, 1))).reason())
                .isEqualTo(RejectionReason.INVALID_STATE);
        verify(holds, never()).save(any());
    }

    private WorkshopSession session(long id, UUID reference, int capacity) {
        WorkshopSession s = new WorkshopSession();
        s.setId(id);
        s.setReference(reference.toString());
        s.setState(SessionState.PUBLISHED);
        s.setCapacity(capacity);
        return s;
    }

    private ReserveGroupCapacityCommand.Line line(String bookingReference, UUID session, int tickets) {
        return new ReserveGroupCapacityCommand.Line(bookingReference, "15", List.of(session), tickets);
    }

    private ReserveGroupCapacityCommand command(ReserveGroupCapacityCommand.Line... lines) {
        return new ReserveGroupCapacityCommand("GRP-1", List.of(lines), "OP-1", "CORR-1");
    }
}
