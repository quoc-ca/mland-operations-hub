package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CapacityHoldService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CheckInService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyCleanupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopTicketConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 003.2 S4 facade evidence on real MySQL: T132 (commit refused once a cancellation is requested), T122
 * (CANCELLATION_REQUESTED to CANCELLED only when no ticket is unresolved), T117 (cancelTicket independent of refund,
 * history kept, scan refused), T118 (per-line participant bounds), T134 (replay after ledger cleanup) and T123
 * (OTA-only ticket revocation).
 */
@SpringBootTest
@ActiveProfiles("workshopoperations-mysql-test")
class FacadeOperationsMySqlIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkshopOperationsFacade facade;
    @Autowired private CapacityHoldService capacityHoldService;
    @Autowired private CheckInService checkInService;
    @Autowired private FacadeCommandIdempotencyCleanupService cleanupService;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private CapacityHoldRepository holdRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private TicketSessionEntitlementRepository entitlementRepository;
    @Autowired private TicketEntitlementReleaseRepository releaseRepository;
    @Autowired private OperationsAuditEventRepository auditRepository;
    @Autowired private AttendanceActionRepository actionRepository;
    @Autowired private CancellationHandoffRepository handoffRepository;

    @MockBean private WorkshopOperationsAuthorizationService authorization;

    private WorkshopLocation location;
    private int sequence;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "FacadeOperationsMySqlIT requires real MySQL evidence");
        }
        location = locationRepository.saveAndFlush(WorkshopLocation.builder()
                .code("S4-" + UUID.randomUUID()).displayName("S4 Facade Location").addressSnapshot("test").build());
        sequence = 0;
        when(authorization.requireStaffSessionAssignment(any(), anyLong())).thenReturn(new AccountSummary(
                1L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@example.com", Instant.now(), Instant.now()));
    }

    // ---------------------------------------------------------------- T132

    @Test
    @DisplayName("T132: commit is accepted on IN_PROGRESS and on a SALES_PAUSED session for a pre-pause hold; a hold during the pause is refused")
    void commitStaysAcceptedOnInProgressAndSalesPaused() {
        WorkshopSession pausedSession = session(SessionState.PUBLISHED, 10, 3);
        WorkshopSession runningSession = session(SessionState.PUBLISHED, 10, 3);
        CapacityHoldOutcome pausedHold = reserve("BK-" + UUID.randomUUID(), pausedSession, 2);
        CapacityHoldOutcome runningHold = reserve("BK-" + UUID.randomUUID(), runningSession, 2);
        setState(pausedSession, SessionState.SALES_PAUSED);
        setState(runningSession, SessionState.IN_PROGRESS);

        CapacityHoldOutcome newHold = reserve("BK-" + UUID.randomUUID(), pausedSession, 1);

        assertThat(newHold.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(newHold.reason()).isEqualTo(RejectionReason.SALE_NOT_ELIGIBLE);
        assertThat(commit(holdBooking(pausedHold), pausedHold, "op-" + UUID.randomUUID()).status())
                .isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(commit(holdBooking(runningHold), runningHold, "op-" + UUID.randomUUID()).status())
                .isEqualTo(OutcomeStatus.ACCEPTED);
    }

    @Test
    @DisplayName("T132: commit on CANCELLATION_REQUESTED is REJECTED INVALID_STATE, holds stay ACTIVE and expire normally, replay repeats the rejection")
    void commitRefusedOnCancellationRequested() {
        WorkshopSession session = session(SessionState.PUBLISHED, 10, 3);
        String booking = "BK-" + UUID.randomUUID();
        CapacityHoldOutcome hold = reserve(booking, session, 2);
        setState(session, SessionState.CANCELLATION_REQUESTED);
        String opRef = "op-" + UUID.randomUUID();

        CapacityCommitOutcome first = commit(booking, hold, opRef);
        CapacityCommitOutcome replay = commit(booking, hold, opRef);

        assertThat(first.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(first.reason()).isEqualTo(RejectionReason.INVALID_STATE);
        assertThat(replay.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(replay.reason()).isEqualTo(RejectionReason.INVALID_STATE);
        CapacityHold stored = holdRepository.findByReference(hold.holdReferences().getFirst().toString()).orElseThrow();
        assertThat(stored.getState()).isEqualTo(CapacityHoldState.ACTIVE);
        assertThat(ticketRepository.findByBookingReference(booking)).isEmpty();

        stored.setExpiresAt(Instant.now().minusSeconds(60));
        holdRepository.saveAndFlush(stored);
        capacityHoldService.expirePendingHolds();
        assertThat(holdRepository.findByReference(stored.getReference()).orElseThrow().getState())
                .isEqualTo(CapacityHoldState.EXPIRED);
    }

    // ---------------------------------------------------------------- T122

    @Test
    @DisplayName("T122: completeCancellation is REJECTED CANCELLATION_HANDOFF_UNRESOLVED while a ticket is unresolved, changes nothing and succeeds on the same operationReference once the last ticket is cancelled")
    void completeCancellationWaitsForEveryTicket() {
        WorkshopSession session = session(SessionState.CANCELLATION_REQUESTED, 10, 3);
        String booking = "BK-" + UUID.randomUUID();
        AttendanceTicket first = ticket(booking, 1, TicketSalesChannel.DIRECT, session);
        AttendanceTicket second = ticket(booking, 2, TicketSalesChannel.DIRECT, session);
        CancellationHandoff handoff = handoffRepository.saveAndFlush(CancellationHandoff.builder()
                .session(session).requesterId(1L).reasonCode("MLAND_CANCEL")
                .cancellationCaseReference("CASE-" + UUID.randomUUID()).build());
        String opRef = "op-complete-" + UUID.randomUUID();
        CompleteCancellationHandoffCommand command = new CompleteCancellationHandoffCommand(
                UUID.fromString(session.getReference()), handoff.getCancellationCaseReference(), opRef, "corr-1");

        assertThat(cancel(first, "op-c1-" + UUID.randomUUID()).status()).isEqualTo(OutcomeStatus.ACCEPTED);
        CancellationCompletionOutcome early = facade.completeCancellationHandoff(command);
        assertThat(early.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(early.reason()).isEqualTo(RejectionReason.CANCELLATION_HANDOFF_UNRESOLVED);
        assertThat(stateOf(session)).isEqualTo(SessionState.CANCELLATION_REQUESTED);
        assertThat(handoffRepository.findById(handoff.getId()).orElseThrow().getStatus())
                .isEqualTo(CancellationHandoffStatus.REQUESTED);
        assertThat(completionAudits(session)).isZero();
        assertThat(facade.completeCancellationHandoff(command).reason())
                .as("repeating while unresolved is safe and answers the same")
                .isEqualTo(RejectionReason.CANCELLATION_HANDOFF_UNRESOLVED);

        assertThat(cancel(second, "op-c2-" + UUID.randomUUID()).status()).isEqualTo(OutcomeStatus.ACCEPTED);
        CancellationCompletionOutcome done = facade.completeCancellationHandoff(command);
        CancellationCompletionOutcome replay = facade.completeCancellationHandoff(command);

        assertThat(done.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(replay.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(stateOf(session)).isEqualTo(SessionState.CANCELLED);
        assertThat(handoffRepository.findById(handoff.getId()).orElseThrow().getStatus())
                .isEqualTo(CancellationHandoffStatus.RESOLVED);
        assertThat(completionAudits(session)).as("one transition audit, none for the replay").isEqualTo(1);
    }

    @Test
    @DisplayName("T122: a session with no ticket completes at once; completion outside CANCELLATION_REQUESTED is refused; CANCELLED refuses check-in")
    void completeCancellationNoTicketNotRequestedAndCheckIn() {
        WorkshopSession empty = session(SessionState.CANCELLATION_REQUESTED, 10, 3);
        WorkshopSession published = session(SessionState.PUBLISHED, 10, 3);

        assertThat(complete(empty, "op-e-" + UUID.randomUUID()).status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(stateOf(empty)).isEqualTo(SessionState.CANCELLED);
        CancellationCompletionOutcome refused = complete(published, "op-p-" + UUID.randomUUID());
        assertThat(refused.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(refused.reason()).isEqualTo(RejectionReason.INVALID_STATE);
        assertThat(stateOf(published)).isEqualTo(SessionState.PUBLISHED);

        AttendanceTicket ticket = ticket("BK-" + UUID.randomUUID(), 1, TicketSalesChannel.DIRECT, empty);
        openCheckIn(empty);
        assertThatThrownBy(() -> checkInService.checkIn("Bearer x", empty.getReference(),
                CheckInRequest.builder().ticketCode(ticket.getTicketCode()).build()))
                .isInstanceOf(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleException.class);
        assertThat(entitlementRepository.findByTicketId(ticket.getId()).getFirst().getAttendanceState())
                .isEqualTo(AttendanceState.NOT_CHECKED_IN);
    }

    // ---------------------------------------------------------------- T117

    @Test
    @DisplayName("T117: cancelling a ticket that attended one of three sessions frees sessions 2-3, keeps session 1, and a later scan is refused with TICKET_CONFLICT")
    void cancelAfterAttendingOneOfThree() {
        WorkshopSession attended = pastSession(SessionState.IN_PROGRESS, 10);
        WorkshopSession second = session(SessionState.PUBLISHED, 10, 3);
        WorkshopSession third = session(SessionState.PUBLISHED, 10, 4);
        String booking = "BK-" + UUID.randomUUID();
        AttendanceTicket ticket = ticket(booking, 1, TicketSalesChannel.DIRECT, attended, second, third);
        TicketSessionEntitlement record = entitlementRepository.findByTicketIdAndSessionId(ticket.getId(), attended.getId()).orElseThrow();
        record.setAttendanceState(AttendanceState.CHECKED_IN);
        record.setCheckedInAt(Instant.now().minusSeconds(3600));
        entitlementRepository.saveAndFlush(record);
        assertThat(remaining(second)).isEqualTo(9);

        TicketCancellationOutcome outcome = cancel(ticket, "op-" + UUID.randomUUID());

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(outcome.releasedSessionReferences()).containsExactlyInAnyOrder(
                UUID.fromString(second.getReference()), UUID.fromString(third.getReference()));
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getValidityState())
                .isEqualTo(TicketValidityState.CANCELLED);
        assertThat(entitlementRepository.findByTicketId(ticket.getId())).singleElement().satisfies(e -> {
            assertThat(e.getSession().getId()).isEqualTo(attended.getId());
            assertThat(e.getAttendanceState()).isEqualTo(AttendanceState.CHECKED_IN);
            assertThat(e.getCheckedInAt()).isNotNull();
        });
        assertThat(remaining(second)).isEqualTo(10);
        assertThat(remaining(third)).isEqualTo(10);

        openCheckIn(second);
        long actionsBefore = actionRepository.count();
        assertThatThrownBy(() -> checkInService.checkIn("Bearer x", second.getReference(),
                CheckInRequest.builder().ticketCode(ticket.getTicketCode()).build()))
                .isInstanceOf(WorkshopTicketConflictException.class);
        assertThat(actionRepository.count()).as("no attendance recorded").isEqualTo(actionsBefore);
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getQrReference())
                .as("the QR is kept, not regenerated").isEqualTo(ticket.getQrReference());
    }

    // ---------------------------------------------------------------- T118

    @Test
    @DisplayName("T118: a mixed-package group with one line below its package minimum fails all lines; fixed, it holds every line (min and max inclusive)")
    void groupBoundsAreCheckedPerLine() {
        WorkshopSession a = session(SessionState.PUBLISHED, 30, 3);
        WorkshopSession b = session(SessionState.PUBLISHED, 30, 4);
        String minThree = packageWith(3, 5);
        String anyOneToTwo = packageWith(1, 2);
        String opRef = "op-g-" + UUID.randomUUID();
        String lineA = "BK-A-" + UUID.randomUUID();
        String lineB = "BK-B-" + UUID.randomUUID();

        GroupCapacityOutcome belowMin = facade.reserveGroupCapacity(group(opRef, List.of(
                line(lineA, minThree, a, 2), line(lineB, anyOneToTwo, b, 2))));

        assertThat(belowMin.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(belowMin.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
        assertThat(belowMin.failingLines()).containsExactly(0);
        assertThat(holdRepository.findByBookingReference(lineA)).isEmpty();
        assertThat(holdRepository.findByBookingReference(lineB)).as("the valid line holds nothing either").isEmpty();

        GroupCapacityOutcome aboveMax = facade.reserveGroupCapacity(group("op-g2-" + UUID.randomUUID(), List.of(
                line(lineA, minThree, a, 6), line(lineB, anyOneToTwo, b, 3))));
        assertThat(aboveMax.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(aboveMax.failingLines()).containsExactly(0, 1);

        GroupCapacityOutcome atBounds = facade.reserveGroupCapacity(group("op-g3-" + UUID.randomUUID(), List.of(
                line(lineA, minThree, a, 3), line(lineB, anyOneToTwo, b, 2))));
        assertThat(atBounds.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(atBounds.lineHoldReferences()).hasSize(2);
        assertThat(holdRepository.findByBookingReference(lineA)).hasSize(1);
        assertThat(holdRepository.findByBookingReference(lineB)).hasSize(1);
    }

    // ---------------------------------------------------------------- T134

    @Test
    @DisplayName("T134: cancelTicket replayed after the ledger row was cleaned up is REJECTED and releases nothing")
    void cancelTicketReplayAfterCleanup() {
        WorkshopSession session = session(SessionState.PUBLISHED, 10, 3);
        AttendanceTicket ticket = ticket("BK-" + UUID.randomUUID(), 1, TicketSalesChannel.DIRECT, session);
        String opRef = "op-t134-" + UUID.randomUUID();
        assertThat(cancel(ticket, opRef).status()).isEqualTo(OutcomeStatus.ACCEPTED);
        int seatsAfter = remaining(session);
        int releasesAfter = releaseRepository.findByTicketIdOrderByIdAsc(ticket.getId()).size();

        cleanLedger(opRef);
        TicketCancellationOutcome replay = cancel(ticket, opRef);

        assertThat(replay.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(replay.reason()).isIn(RejectionReason.INVALID_STATE, RejectionReason.TICKET_NOT_FOUND);
        assertThat(replay.releasedSessionReferences()).isEmpty();
        assertThat(remaining(session)).isEqualTo(seatsAfter);
        assertThat(releaseRepository.findByTicketIdOrderByIdAsc(ticket.getId())).hasSize(releasesAfter);
    }

    @Test
    @DisplayName("T134: commitBookingHolds and releaseBookingHolds replayed after cleanup change no seat count and issue no second ticket")
    void commitAndReleaseReplayAfterCleanup() {
        WorkshopSession session = session(SessionState.PUBLISHED, 10, 3);
        String booking = "BK-" + UUID.randomUUID();
        CapacityHoldOutcome hold = reserve(booking, session, 2);
        String commitOp = "op-commit-" + UUID.randomUUID();
        assertThat(commit(booking, hold, commitOp).status()).isEqualTo(OutcomeStatus.ACCEPTED);
        int seats = remaining(session);
        int tickets = ticketRepository.findByBookingReference(booking).size();

        cleanLedger(commitOp);
        CapacityCommitOutcome commitReplay = commit(booking, hold, commitOp);

        assertThat(commitReplay.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(remaining(session)).isEqualTo(seats);
        assertThat(ticketRepository.findByBookingReference(booking)).hasSize(tickets);

        String releaseBooking = "BK-" + UUID.randomUUID();
        CapacityHoldOutcome other = reserve(releaseBooking, session, 1);
        String releaseOp = "op-release-" + UUID.randomUUID();
        assertThat(facade.releaseBookingHolds(new ReleaseBookingHoldsCommand(
                releaseBooking, other.holdReferences(), releaseOp, "corr")).status()).isEqualTo(OutcomeStatus.ACCEPTED);
        int seatsAfterRelease = remaining(session);

        cleanLedger(releaseOp);
        facade.releaseBookingHolds(new ReleaseBookingHoldsCommand(releaseBooking, other.holdReferences(), releaseOp, "corr"));

        assertThat(remaining(session)).isEqualTo(seatsAfterRelease);
        assertThat(holdRepository.findByReference(other.holdReferences().getFirst().toString()).orElseThrow().getState())
                .isEqualTo(CapacityHoldState.RELEASED);
    }

    // ---------------------------------------------------------------- T123

    @Test
    @DisplayName("T123: partial OTA revocation revokes the named tickets only, frees their future seats and keeps the rest ACTIVE")
    void otaPartialRevoke() {
        WorkshopSession first = session(SessionState.PUBLISHED, 10, 3);
        WorkshopSession second = session(SessionState.PUBLISHED, 10, 4);
        String booking = "OTA-" + UUID.randomUUID();
        AttendanceTicket one = ticket(booking, 1, TicketSalesChannel.OTA, first, second);
        AttendanceTicket two = ticket(booking, 2, TicketSalesChannel.OTA, first, second);
        AttendanceTicket three = ticket(booking, 3, TicketSalesChannel.OTA, first, second);
        assertThat(remaining(first)).isEqualTo(7);

        OtaTicketRevocationOutcome outcome = revoke(booking, "ota-event-" + UUID.randomUUID(), one, two);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(outcome.revokedTicketReferences()).containsExactly(uuid(one), uuid(two));
        assertThat(validity(one)).isEqualTo(TicketValidityState.REVOKED);
        assertThat(validity(two)).isEqualTo(TicketValidityState.REVOKED);
        assertThat(validity(three)).isEqualTo(TicketValidityState.ACTIVE);
        assertThat(entitlementRepository.findByTicketId(one.getId())).isEmpty();
        assertThat(entitlementRepository.findByTicketId(three.getId())).hasSize(2);
        assertThat(remaining(first)).isEqualTo(9);
        assertThat(remaining(second)).isEqualTo(9);
    }

    @Test
    @DisplayName("T123: replay of the same OTA event changes nothing and returns the same result; a changed ticket set under the same reference conflicts")
    void otaRevokeIdempotent() {
        WorkshopSession session = session(SessionState.PUBLISHED, 10, 3);
        String booking = "OTA-" + UUID.randomUUID();
        AttendanceTicket one = ticket(booking, 1, TicketSalesChannel.OTA, session);
        AttendanceTicket two = ticket(booking, 2, TicketSalesChannel.OTA, session);
        String event = "ota-event-" + UUID.randomUUID();

        OtaTicketRevocationOutcome first = revoke(booking, event, one);
        int seats = remaining(session);
        OtaTicketRevocationOutcome replay = revoke(booking, event, one);
        OtaTicketRevocationOutcome changed = revoke(booking, event, one, two);

        assertThat(first.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(replay).isEqualTo(first);
        assertThat(remaining(session)).isEqualTo(seats);
        assertThat(releaseRepository.findByTicketIdOrderByIdAsc(one.getId())).hasSize(1);
        assertThat(changed.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(changed.reason()).isEqualTo(RejectionReason.INVALID_STATE);
        assertThat(validity(two)).isEqualTo(TicketValidityState.ACTIVE);
    }

    @Test
    @DisplayName("T123: a non-OTA ticket, a checked-in ticket or a foreign booking refuses the whole command with nothing changed")
    void otaRevokeRefusals() {
        WorkshopSession session = session(SessionState.PUBLISHED, 10, 3);
        String booking = "OTA-" + UUID.randomUUID();
        AttendanceTicket ota = ticket(booking, 1, TicketSalesChannel.OTA, session);
        AttendanceTicket direct = ticket(booking, 2, TicketSalesChannel.DIRECT, session);
        AttendanceTicket attended = ticket(booking, 3, TicketSalesChannel.OTA, session);
        TicketSessionEntitlement record = entitlementRepository.findByTicketId(attended.getId()).getFirst();
        record.setAttendanceState(AttendanceState.CHECKED_IN);
        record.setCheckedInAt(Instant.now());
        entitlementRepository.saveAndFlush(record);
        int seats = remaining(session);

        OtaTicketRevocationOutcome nonOta = revoke(booking, "ota-" + UUID.randomUUID(), ota, direct);
        OtaTicketRevocationOutcome checkedIn = revoke(booking, "ota-" + UUID.randomUUID(), ota, attended);
        OtaTicketRevocationOutcome foreign = revoke("OTA-OTHER", "ota-" + UUID.randomUUID(), ota);

        assertThat(nonOta.reason()).isEqualTo(RejectionReason.INVALID_STATE);
        assertThat(checkedIn.reason()).isEqualTo(RejectionReason.TICKET_ALREADY_CHECKED_IN);
        assertThat(foreign.reason()).isEqualTo(RejectionReason.TICKET_NOT_FOUND);
        assertThat(validity(ota)).isEqualTo(TicketValidityState.ACTIVE);
        assertThat(validity(direct)).isEqualTo(TicketValidityState.ACTIVE);
        assertThat(validity(attended)).isEqualTo(TicketValidityState.ACTIVE);
        assertThat(remaining(session)).isEqualTo(seats);
    }

    @Test
    @DisplayName("T123: cancelTicket still refuses an OTA ticket for a general caller (FR-036)")
    void otaTicketStillRefusedByCancelTicket() {
        WorkshopSession session = session(SessionState.PUBLISHED, 10, 3);
        AttendanceTicket ota = ticket("OTA-" + UUID.randomUUID(), 1, TicketSalesChannel.OTA, session);

        TicketCancellationOutcome outcome = cancel(ota, "op-" + UUID.randomUUID());

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.OTA_TICKET_NOT_SUPPORTED);
        assertThat(validity(ota)).isEqualTo(TicketValidityState.ACTIVE);
    }

    // ---------------------------------------------------------------- helpers

    private OtaTicketRevocationOutcome revoke(String booking, String event, AttendanceTicket... tickets) {
        return facade.revokeOtaTickets(new RevokeOtaTicketsCommand(booking,
                java.util.Arrays.stream(tickets).map(this::uuid).toList(), event, "corr"));
    }

    private TicketCancellationOutcome cancel(AttendanceTicket ticket, String opRef) {
        return facade.cancelTicket(new CancelTicketCommand(uuid(ticket), "CASE-" + opRef, opRef, "corr"));
    }

    private CancellationCompletionOutcome complete(WorkshopSession session, String opRef) {
        return facade.completeCancellationHandoff(new CompleteCancellationHandoffCommand(
                UUID.fromString(session.getReference()), "CASE-" + opRef, opRef, "corr"));
    }

    private CapacityHoldOutcome reserve(String booking, WorkshopSession session, int tickets) {
        String pkg = packageWith(1, 100);
        return facade.reserveBookingCapacity(new ReserveBookingCapacityCommand(booking, pkg,
                List.of(UUID.fromString(session.getReference())), tickets, "op-" + UUID.randomUUID(), "corr"));
    }

    private String holdBooking(CapacityHoldOutcome hold) {
        return holdRepository.findByReference(hold.holdReferences().getFirst().toString()).orElseThrow().getBookingReference();
    }

    private CapacityCommitOutcome commit(String booking, CapacityHoldOutcome hold, String opRef) {
        return facade.commitBookingHolds(new CommitBookingHoldsCommand(booking, hold.holdReferences(), opRef, "corr"));
    }

    private String packageWith(int min, int max) {
        return String.valueOf(packageRepository.save(WorkshopPackage.builder()
                .name("S4 package " + UUID.randomUUID()).description("fixture").publicPrice(1000L)
                .minParticipants(min).maxParticipants(max).requiredSessionCount(1)
                .sameLocationRequired(false).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()).getId());
    }

    private ReserveGroupCapacityCommand.Line line(String booking, String pkg, WorkshopSession session, int tickets) {
        return new ReserveGroupCapacityCommand.Line(booking, pkg, List.of(UUID.fromString(session.getReference())), tickets);
    }

    private ReserveGroupCapacityCommand group(String opRef, List<ReserveGroupCapacityCommand.Line> lines) {
        return new ReserveGroupCapacityCommand("GROUP-" + UUID.randomUUID(), lines, opRef, "corr");
    }

    private void cleanLedger(String opRef) {
        jdbc.update("UPDATE facade_command_idempotency_records SET completed_at = ? WHERE operation_reference = ?",
                java.sql.Timestamp.from(Instant.now().minus(48, ChronoUnit.HOURS)), opRef);
        assertThat(cleanupService.cleanupExpiredCompletedRecords()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facade_command_idempotency_records WHERE operation_reference = ?",
                Integer.class, opRef)).isZero();
    }

    private int remaining(WorkshopSession session) {
        return facade.getSaleEligibility(new SaleEligibilityQuery(UUID.fromString(session.getReference()))).remainingCapacity();
    }

    private long completionAudits(WorkshopSession session) {
        return auditRepository.findAll().stream()
                .filter(e -> "FACADE_COMPLETE_CANCELLATION".equals(e.getAction())
                        && session.getId().equals(e.getSessionId())).count();
    }

    private SessionState stateOf(WorkshopSession session) {
        return sessionRepository.findById(session.getId()).orElseThrow().getState();
    }

    private void setState(WorkshopSession session, SessionState state) {
        WorkshopSession stored = sessionRepository.findById(session.getId()).orElseThrow();
        stored.setState(state);
        sessionRepository.saveAndFlush(stored);
    }

    private void openCheckIn(WorkshopSession session) {
        WorkshopSession stored = sessionRepository.findById(session.getId()).orElseThrow();
        stored.setCheckInOpensAt(Instant.now().minusSeconds(7200));
        sessionRepository.saveAndFlush(stored);
    }

    private TicketValidityState validity(AttendanceTicket ticket) {
        return ticketRepository.findById(ticket.getId()).orElseThrow().getValidityState();
    }

    private UUID uuid(AttendanceTicket ticket) {
        return UUID.fromString(ticket.getReference());
    }

    private WorkshopSession session(SessionState state, int capacity, int daysAhead) {
        LocalDate date = LocalDate.now(ZONE).plusDays(daysAhead);
        return sessionAt(state, capacity, date);
    }

    private WorkshopSession pastSession(SessionState state, int capacity) {
        return sessionAt(state, capacity, LocalDate.now(ZONE).minusDays(1));
    }

    private WorkshopSession sessionAt(SessionState state, int capacity, LocalDate date) {
        LocalTime startTime = LocalTime.of(8, 0).plusMinutes(sequence++ * 20L);
        Instant start = date.atTime(startTime).atZone(ZONE).toInstant();
        WorkshopSlot slot = slotRepository.saveAndFlush(WorkshopSlot.builder()
                .location(location).label("S4 slot " + UUID.randomUUID())
                .startTime(startTime).endTime(startTime.plusMinutes(15)).defaultCapacity(capacity).build());
        return sessionRepository.saveAndFlush(WorkshopSession.builder()
                .location(location).slot(slot).workshopDate(date)
                .scheduledStartAt(start).scheduledEndAt(start.plusSeconds(900))
                .checkInOpensAt(start.minusSeconds(1800)).capacity(capacity).state(state).build());
    }

    private AttendanceTicket ticket(String booking, int sequenceNumber, TicketSalesChannel channel,
                                    WorkshopSession... sessions) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        AttendanceTicket ticket = AttendanceTicket.builder()
                .bookingReference(booking).ticketSequence(sequenceNumber).ticketCode("S4-" + suffix)
                .qrReference("QR-" + UUID.randomUUID()).validityState(TicketValidityState.ACTIVE).build();
        ticket.setSalesChannel(channel);
        AttendanceTicket saved = ticketRepository.saveAndFlush(ticket);
        for (WorkshopSession session : sessions) {
            entitlementRepository.save(TicketSessionEntitlement.builder().ticket(saved).session(session)
                    .attendanceState(AttendanceState.NOT_CHECKED_IN).build());
        }
        entitlementRepository.flush();
        return saved;
    }
}
