package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLifecycleService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingValidationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T007 & T014: Integration test verifying Booking lifecycle orchestration,
 * local MySQL transaction boundaries, all-or-nothing capacity operations,
 * post-commit notification handoff, and fault tolerance.
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkshopBookingLifecycleIT {

    @Autowired
    private BookingLifecycleService bookingLifecycleService;

    @Autowired
    private WorkshopBookingFacade bookingFacade;

    @Autowired
    private WorkshopBookingRepository bookingRepository;

    @Autowired
    private BookingSessionCommitmentRepository commitmentRepository;

    @Autowired
    private BookingCapacityHoldRepository holdRepository;

    @Autowired
    private BookingInvoiceRepository invoiceRepository;

    @Autowired
    private BookingPaymentReceiptRepository paymentReceiptRepository;

    @Autowired
    private BookingTicketRepository ticketRepository;

    @Autowired
    private BookingTicketSessionEntitlementRepository entitlementRepository;

    @Autowired
    private MagicLinkAccessRepository magicLinkRepository;

    @Autowired
    private BookingNotificationIntentRepository notificationIntentRepository;

    @Autowired
    private BookingAuditEvidenceRepository auditEvidenceRepository;

    @Autowired
    private TestNotificationEventListener testNotificationListener;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private WorkshopOperationsFacade mockOperationsFacade;

    @MockBean
    private AccountFacade accountFacade;

    private Long testLocationId;

    @BeforeEach
    void setUp() {
        testNotificationListener.clear();
        testNotificationListener.setSimulateDeliveryFailure(false);

        List<Long> locIds = jdbcTemplate.queryForList("SELECT id FROM workshop_locations LIMIT 1", Long.class);
        if (locIds.isEmpty()) {
            jdbcTemplate.update("INSERT INTO workshop_locations (code, display_name, address_snapshot, active, created_at, updated_at) VALUES ('LOC-TEST-01', 'Test Loc', '123 Test St', 1, NOW(), NOW())");
            testLocationId = jdbcTemplate.queryForObject("SELECT id FROM workshop_locations WHERE code = 'LOC-TEST-01'", Long.class);
        } else {
            testLocationId = locIds.get(0);
        }
    }

    // =========================================================================
    // T014: All-or-nothing Reserve Orchestration
    // =========================================================================

    @Test
    @DisplayName("T014-1: All-or-nothing reserve: When all sessions are eligible, creates pending booking, invoice, holds and minimal audit")
    void verifyAllOrNothingReserveSuccess() {
        String bookingCode = "BK-RES-SUCC-" + UUID.randomUUID().toString().substring(0, 8);
        UUID sess1Uuid = new UUID(0L, 101L);
        UUID sess2Uuid = new UUID(0L, 102L);
        UUID hold1Uuid = UUID.randomUUID();
        UUID hold2Uuid = UUID.randomUUID();

        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any(SaleEligibilityQuery.class))).thenReturn(eligibleSnap);

        CapacityHoldOutcome holdOutcome = CapacityHoldOutcome.accepted(
                List.of(hold1Uuid, hold2Uuid), Instant.now().plus(15, ChronoUnit.MINUTES));
        when(mockOperationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class))).thenReturn(holdOutcome);

        WorkshopBooking booking = bookingLifecycleService.createPendingBookingReservation(
                bookingCode,
                1L,
                "Pottery Package 101",
                "customer@mland.vn",
                "0987654321",
                50L,
                2,
                List.of(101L, 102L),
                500000L,
                1000000L,
                "OP-RES-1",
                "CORR-RES-1"
        );

        assertThat(booking).isNotNull();
        assertThat(booking.getBookingCode()).isEqualTo(bookingCode);
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(booking.getHoldExpiresAt()).isNotNull();

        // Verify Commitments
        List<BookingSessionCommitment> commitments = commitmentRepository.findByBookingIdOrderByOrdinalAsc(booking.getId());
        assertThat(commitments).hasSize(2);
        assertThat(commitments.get(0).getSessionId()).isEqualTo(101L);
        assertThat(commitments.get(1).getSessionId()).isEqualTo(102L);

        // Verify Holds
        List<BookingCapacityHold> holds = holdRepository.findByBookingId(booking.getId());
        assertThat(holds).hasSize(2);
        assertThat(holds).allMatch(h -> "RESERVED".equalsIgnoreCase(h.getHoldStatus()));

        // Verify Invoice
        BookingInvoice invoice = invoiceRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(invoice.getPackageTotal()).isEqualTo(1000000L);
        assertThat(invoice.getDepositAmount()).isEqualTo(500000L);
        assertThat(invoice.getRemainingAmount()).isEqualTo(500000L);

        // Verify Audit Evidence
        List<BookingAuditEvidence> audits = auditEvidenceRepository.findByBookingCodeOrderByOccurredAtDesc(bookingCode);
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).getAction()).isEqualTo("RESERVE_CAPACITY");
        assertThat(audits.get(0).getOutcome()).isEqualTo("ACCEPTED");
        assertThat(audits.get(0).getCorrelationReference()).isEqualTo("CORR-RES-1");

        // Verify zero notification intent for pending reservation
        List<BookingNotificationIntent> notifs = notificationIntentRepository.findByBookingId(booking.getId());
        assertThat(notifs).isEmpty();
    }

    @Test
    @DisplayName("T014-2: All-or-nothing reserve: When one session is ineligible, rejects with zero side effects")
    void verifyAllOrNothingReserveRejectionWhenSessionIneligible() {
        String bookingCode = "BK-RES-FAIL-" + UUID.randomUUID().toString().substring(0, 8);
        UUID sess1Uuid = new UUID(0L, 201L);
        UUID sess2Uuid = new UUID(0L, 202L);

        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        SaleEligibilitySnapshot fullSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 0);

        when(mockOperationsFacade.getSaleEligibility(new SaleEligibilityQuery(sess1Uuid))).thenReturn(eligibleSnap);
        when(mockOperationsFacade.getSaleEligibility(new SaleEligibilityQuery(sess2Uuid))).thenReturn(fullSnap);

        assertThatThrownBy(() -> bookingLifecycleService.createPendingBookingReservation(
                bookingCode,
                1L,
                "Pottery Package 101",
                "customer@mland.vn",
                "0987654321",
                50L,
                2,
                List.of(201L, 202L),
                500000L,
                1000000L,
                "OP-RES-2",
                "CORR-RES-2"
        )).isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("does not have sufficient capacity");

        // Operations reserve must NEVER be called
        verify(mockOperationsFacade, never()).reserveBookingCapacity(any());

        // Zero records in DB for this bookingCode
        assertThat(bookingRepository.findByBookingCode(bookingCode)).isEmpty();
    }

    @Test
    @DisplayName("T014-3: All-or-nothing reserve: When Operations rejects hold reservation, rolls back completely")
    void verifyAllOrNothingReserveRejectionByOperationsRollsBack() {
        String bookingCode = "BK-RES-REJ-" + UUID.randomUUID().toString().substring(0, 8);
        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any())).thenReturn(eligibleSnap);

        CapacityHoldOutcome rejectedOutcome = CapacityHoldOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY);
        when(mockOperationsFacade.reserveBookingCapacity(any())).thenReturn(rejectedOutcome);

        assertThatThrownBy(() -> bookingLifecycleService.createPendingBookingReservation(
                bookingCode,
                1L,
                "Pottery Package 101",
                "customer@mland.vn",
                "0987654321",
                50L,
                2,
                List.of(301L),
                500000L,
                500000L,
                "OP-RES-3",
                "CORR-RES-3"
        )).isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("Capacity reservation rejected");

        assertThat(bookingRepository.findByBookingCode(bookingCode)).isEmpty();
    }

    // =========================================================================
    // T014: Release Pending Holds Orchestration
    // =========================================================================

    @Test
    @DisplayName("T014-4: Release pending holds: Idempotently releases active holds and cancels pending booking")
    void verifyReleasePendingHoldsIdempotency() {
        String bookingCode = "BK-REL-" + UUID.randomUUID().toString().substring(0, 8);
        UUID holdUuid = UUID.randomUUID();

        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any())).thenReturn(eligibleSnap);
        when(mockOperationsFacade.reserveBookingCapacity(any())).thenReturn(
                CapacityHoldOutcome.accepted(List.of(holdUuid), Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(mockOperationsFacade.releaseBookingHolds(any())).thenReturn(CapacityReleaseOutcome.accepted());

        WorkshopBooking booking = bookingLifecycleService.createPendingBookingReservation(
                bookingCode, 1L, "Pkg", "c@m.vn", "090", 1L, 1, List.of(401L), 300000L, 300000L, "OP", "CORR");

        // 1st release
        bookingLifecycleService.releasePendingHold(bookingCode, "OP-REL-1", "CORR-REL-1");

        WorkshopBooking cancelledBooking = bookingRepository.findByBookingCode(bookingCode).orElseThrow();
        assertThat(cancelledBooking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelledBooking.getCancelledAt()).isNotNull();

        List<BookingCapacityHold> holds = holdRepository.findByBookingId(booking.getId());
        assertThat(holds.get(0).getHoldStatus()).isEqualTo("RELEASED");

        verify(mockOperationsFacade, times(1)).releaseBookingHolds(any());

        // 2nd release (idempotent repeat)
        bookingLifecycleService.releasePendingHold(bookingCode, "OP-REL-2", "CORR-REL-2");
        // Must not call operations facade a second time
        verify(mockOperationsFacade, times(1)).releaseBookingHolds(any());
    }

    // =========================================================================
    // T007 & T014: Confirm Booking, Post-Commit Handoff, and Fault Tolerance
    // =========================================================================

    @Test
    @DisplayName("T007 & T014-5: Confirm booking: Commits holds, reads ticket projection ONLY after commit, creates notification intent & audit in same tx")
    void verifyConfirmBookingOrchestrationAndSameTxIntent() {
        String bookingCode = "BK-CONF-" + UUID.randomUUID().toString().substring(0, 8);
        UUID holdUuid = UUID.randomUUID();

        // Setup pending booking
        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any())).thenReturn(eligibleSnap);
        when(mockOperationsFacade.reserveBookingCapacity(any())).thenReturn(
                CapacityHoldOutcome.accepted(List.of(holdUuid), Instant.now().plus(15, ChronoUnit.MINUTES)));

        WorkshopBooking pending = bookingLifecycleService.createPendingBookingReservation(
                bookingCode, 1L, "Pkg Test", "buyer@mland.vn", "0901234567", 10L, 1, List.of(501L), 400000L, 400000L, "OP-INIT", "CORR-INIT");

        // Setup operations commit and ticket projection
        when(mockOperationsFacade.commitBookingHolds(any(CommitBookingHoldsCommand.class))).thenReturn(CapacityCommitOutcome.accepted());

        List<SessionEntitlementProjection> entitlements = List.of(
                new SessionEntitlementProjection(testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200)));
        UUID ticketRef = UUID.randomUUID();
        DeliverySafeTicket safeTicket = new DeliverySafeTicket(ticketRef, "TK-" + bookingCode + "-1", entitlements);
        DeliverySafeTicketSet ticketSet = new DeliverySafeTicketSet(List.of(safeTicket));
        when(mockOperationsFacade.getBookingTicketProjection(any(BookingTicketProjectionQuery.class))).thenReturn(ticketSet);

        // Act: Confirm booking payment and commit holds
        WorkshopBooking confirmed = bookingLifecycleService.confirmBookingPaymentAndCommitHolds(
                bookingCode,
                "PAY-EVT-" + UUID.randomUUID(),
                "VNPAY",
                200000L, // deposit is ceil(400000 / 2) = 200000
                "PAYLOAD-HASH-999",
                "OP-CONF-1",
                "CORR-CONF-1"
        );

        // InOrder verification: commitBookingHolds MUST precede getBookingTicketProjection
        InOrder inOrder = inOrder(mockOperationsFacade);
        inOrder.verify(mockOperationsFacade).commitBookingHolds(any(CommitBookingHoldsCommand.class));
        inOrder.verify(mockOperationsFacade).getBookingTicketProjection(any(BookingTicketProjectionQuery.class));

        // State assertions
        assertThat(confirmed.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.getPaymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_PAID);
        assertThat(confirmed.getConfirmedAt()).isNotNull();

        // Holds committed
        List<BookingCapacityHold> holds = holdRepository.findByBookingId(confirmed.getId());
        assertThat(holds.get(0).getHoldStatus()).isEqualTo("COMMITTED");

        // Tickets persisted
        List<BookingTicket> tickets = ticketRepository.findByBookingId(confirmed.getId());
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getTicketCode()).isEqualTo("TK-" + bookingCode + "-1");
        assertThat(tickets.get(0).getQrReference()).isEqualTo(ticketRef.toString());

        // Magic link access persisted
        List<MagicLinkAccess> magicLinks = magicLinkRepository.findByBookingId(confirmed.getId());
        assertThat(magicLinks).hasSize(1);
        assertThat(magicLinks.get(0).getTokenHash()).isNotBlank();
        assertThat(magicLinks.get(0).getLifecycleStatus()).isEqualTo(MagicLinkLifecycleStatus.ACTIVE);

        // Notification intent and booking audit created in the same local transaction
        List<BookingNotificationIntent> intents = notificationIntentRepository.findByBookingId(confirmed.getId());
        assertThat(intents).hasSize(1);
        BookingNotificationIntent intent = intents.get(0);
        assertThat(intent.getNotificationKind()).isEqualTo("CONFIRMED_DELIVERY");
        assertThat(intent.getCorrelationReference()).isEqualTo("CORR-CONF-1");
        assertThat(intent.getDeliverySnapshot()).contains("buyer@mland.vn");
        assertThat(intent.getDeliverySnapshot()).contains("TK-" + bookingCode + "-1");

        List<BookingAuditEvidence> audits = auditEvidenceRepository.findByBookingCodeOrderByOccurredAtDesc(bookingCode);
        Optional<BookingAuditEvidence> confirmAudit = audits.stream()
                .filter(a -> "PAYMENT_CONFIRMED".equals(a.getAction()))
                .findFirst();
        assertThat(confirmAudit).isPresent();
        assertThat(confirmAudit.get().getCorrelationReference()).isEqualTo("CORR-CONF-1");

        // Post-commit handoff event assertions
        assertThat(testNotificationListener.getReceivedEvents()).hasSize(1);
        BookingNotificationReady handoff = testNotificationListener.getReceivedEvents().get(0);
        assertThat(handoff.notificationReference()).isEqualTo(intent.getNotificationReference());
        assertThat(handoff.bookingReference()).isEqualTo(bookingCode);
        assertThat(handoff.notificationKind()).isEqualTo("CONFIRMED_DELIVERY");
        assertThat(handoff.correlationReference()).isEqualTo("CORR-CONF-1");

        // Delivery payload query via WorkshopBookingFacade
        BookingNotificationDeliveryPayload payload = bookingFacade.getBookingNotificationDeliveryPayload(
                new BookingNotificationDeliveryPayloadQuery(handoff.notificationReference()));
        assertThat(payload).isNotNull();
        assertThat(payload.notificationKind()).isEqualTo("CONFIRMED_DELIVERY");
        assertThat(payload.storedRecipient()).isEqualTo("buyer@mland.vn");
        assertThat(payload.renderedTicketArtifacts()).contains("TK-" + bookingCode + "-1");
        assertThat(payload.bookingAccessLink()).isNotBlank();
        assertThat(payload.correlationReference()).isEqualTo("CORR-CONF-1");
    }

    @Test
    @DisplayName("T007-6: Delivery failure in post-commit handoff never rolls back or reverses confirmed booking outcome")
    void verifyDeliveryFailureDoesNotReverseBookingOutcome() {
        String bookingCode = "BK-FAIL-NOTIF-" + UUID.randomUUID().toString().substring(0, 8);
        UUID holdUuid = UUID.randomUUID();

        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any())).thenReturn(eligibleSnap);
        when(mockOperationsFacade.reserveBookingCapacity(any())).thenReturn(
                CapacityHoldOutcome.accepted(List.of(holdUuid), Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(mockOperationsFacade.commitBookingHolds(any())).thenReturn(CapacityCommitOutcome.accepted());
        when(mockOperationsFacade.getBookingTicketProjection(any())).thenReturn(new DeliverySafeTicketSet(List.of()));

        bookingLifecycleService.createPendingBookingReservation(
                bookingCode, 1L, "Pkg Test", "fail@mland.vn", "0901234567", 10L, 1, List.of(601L), 300000L, 300000L, "OP", "CORR");

        // Simulate notification listener throwing exception
        testNotificationListener.setSimulateDeliveryFailure(true);

        // Confirm booking: should complete without throwing exception to caller
        WorkshopBooking confirmed = bookingLifecycleService.confirmBookingPaymentAndCommitHolds(
                bookingCode, "EVT-" + UUID.randomUUID(), "VNPAY", 150000L, "HASH-1", "OP-1", "CORR-1");

        // Booking remains CONFIRMED in DB
        WorkshopBooking inDb = bookingRepository.findByBookingCode(bookingCode).orElseThrow();
        assertThat(inDb.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(inDb.getPaymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_PAID);

        // Holds remain committed
        List<BookingCapacityHold> holds = holdRepository.findByBookingId(inDb.getId());
        assertThat(holds.get(0).getHoldStatus()).isEqualTo("COMMITTED");

        // Notification intent remains persisted
        List<BookingNotificationIntent> intents = notificationIntentRepository.findByBookingId(inDb.getId());
        assertThat(intents).hasSize(1);
    }

    @Test
    @DisplayName("T014-7: Confirm booking: When hold is expired, rejects without committing holds or querying tickets")
    void verifyConfirmBookingRejectsExpiredHold() {
        String bookingCode = "BK-EXP-" + UUID.randomUUID().toString().substring(0, 8);
        UUID holdUuid = UUID.randomUUID();

        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any())).thenReturn(eligibleSnap);
        // Expired hold in the past
        when(mockOperationsFacade.reserveBookingCapacity(any())).thenReturn(
                CapacityHoldOutcome.accepted(List.of(holdUuid), Instant.now().minus(5, ChronoUnit.MINUTES)));

        WorkshopBooking booking = bookingLifecycleService.createPendingBookingReservation(
                bookingCode, 1L, "Pkg", "c@m.vn", "090", 1L, 1, List.of(701L), 200000L, 200000L, "OP", "CORR");

        assertThatThrownBy(() -> bookingLifecycleService.confirmBookingPaymentAndCommitHolds(
                bookingCode, "EVT", "VNPAY", 100000L, "HASH", "OP", "CORR"))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("expired");

        // Operations commit & ticket projection must NEVER be called
        verify(mockOperationsFacade, never()).commitBookingHolds(any());
        verify(mockOperationsFacade, never()).getBookingTicketProjection(any());

        // Zero tickets or notification intents in DB
        assertThat(ticketRepository.findByBookingId(booking.getId())).isEmpty();
        assertThat(notificationIntentRepository.findByBookingId(booking.getId())).isEmpty();
    }

    @Test
    @DisplayName("T014-8: Confirm booking: When deposit amount mismatches, rejects without side effects")
    void verifyConfirmBookingRejectsMismatchedAmount() {
        String bookingCode = "BK-MISMATCH-" + UUID.randomUUID().toString().substring(0, 8);
        UUID holdUuid = UUID.randomUUID();

        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any())).thenReturn(eligibleSnap);
        when(mockOperationsFacade.reserveBookingCapacity(any())).thenReturn(
                CapacityHoldOutcome.accepted(List.of(holdUuid), Instant.now().plus(15, ChronoUnit.MINUTES)));

        WorkshopBooking booking = bookingLifecycleService.createPendingBookingReservation(
                bookingCode, 1L, "Pkg", "c@m.vn", "090", 1L, 1, List.of(801L), 200000L, 200000L, "OP", "CORR");

        assertThatThrownBy(() -> bookingLifecycleService.confirmBookingPaymentAndCommitHolds(
                bookingCode, "EVT", "VNPAY", 99999L, "HASH", "OP", "CORR"))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("Received amount does not match deposit requirement");

        verify(mockOperationsFacade, never()).commitBookingHolds(any());
        verify(mockOperationsFacade, never()).getBookingTicketProjection(any());
    }

    @Test
    @DisplayName("T014-9: Confirm booking: When Operations rejects commit, rolls back and never queries ticket projection")
    void verifyConfirmBookingRejectionByOperationsRollsBack() {
        String bookingCode = "BK-REJ-COMMIT-" + UUID.randomUUID().toString().substring(0, 8);
        UUID holdUuid = UUID.randomUUID();

        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any())).thenReturn(eligibleSnap);
        when(mockOperationsFacade.reserveBookingCapacity(any())).thenReturn(
                CapacityHoldOutcome.accepted(List.of(holdUuid), Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(mockOperationsFacade.commitBookingHolds(any())).thenReturn(
                CapacityCommitOutcome.rejected(RejectionReason.HOLD_MISSING_OR_EXPIRED));

        WorkshopBooking booking = bookingLifecycleService.createPendingBookingReservation(
                bookingCode, 1L, "Pkg", "c@m.vn", "090", 1L, 1, List.of(901L), 200000L, 200000L, "OP", "CORR");

        assertThatThrownBy(() -> bookingLifecycleService.confirmBookingPaymentAndCommitHolds(
                bookingCode, "EVT", "VNPAY", 100000L, "HASH", "OP", "CORR"))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("Hold commit rejected by Workshop Operations");

        // Ticket projection MUST NOT be queried if commit was rejected
        verify(mockOperationsFacade, never()).getBookingTicketProjection(any());

        // Booking remains PENDING_PAYMENT
        WorkshopBooking inDb = bookingRepository.findByBookingCode(bookingCode).orElseThrow();
        assertThat(inDb.getBookingStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        // Zero tickets and zero notification intent
        assertThat(ticketRepository.findByBookingId(booking.getId())).isEmpty();
        assertThat(notificationIntentRepository.findByBookingId(booking.getId())).isEmpty();
    }

    // =========================================================================
    // Facade Integration: Financial Projection, Final Balance, and Redelivery
    // =========================================================================

    @Test
    @DisplayName("T007 & T014-10: Facade contract: Confirmed booking financial projection and final balance resolution")
    void verifyFacadeFinancialProjectionAndFinalBalance() {
        String bookingCode = "BK-FACADE-" + UUID.randomUUID().toString().substring(0, 8);
        UUID holdUuid = UUID.randomUUID();

        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any())).thenReturn(eligibleSnap);
        when(mockOperationsFacade.reserveBookingCapacity(any())).thenReturn(
                CapacityHoldOutcome.accepted(List.of(holdUuid), Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(mockOperationsFacade.commitBookingHolds(any())).thenReturn(CapacityCommitOutcome.accepted());
        when(mockOperationsFacade.getBookingTicketProjection(any())).thenReturn(new DeliverySafeTicketSet(List.of()));

        bookingLifecycleService.createPendingBookingReservation(
                bookingCode, 42L, "Masterclass", "master@mland.vn", "0901234567", 15L, 2, List.of(1001L), 600000L, 1200000L, "OP", "CORR");

        // Before confirmation, projection is null
        assertThat(bookingFacade.getConfirmedBookingFinancialProjection(new ConfirmedBookingFinancialProjectionQuery(bookingCode))).isNull();

        // Confirm
        bookingLifecycleService.confirmBookingPaymentAndCommitHolds(
                bookingCode, "EVT-FIN-" + UUID.randomUUID(), "VNPAY", 600000L, "HASH-FIN", "OP-FIN", "CORR-FIN");

        // After confirmation, projection is populated
        ConfirmedBookingFinancialProjection projection = bookingFacade.getConfirmedBookingFinancialProjection(
                new ConfirmedBookingFinancialProjectionQuery(bookingCode));
        assertThat(projection).isNotNull();
        assertThat(projection.packageReference()).isEqualTo("42");
        assertThat(projection.frozenPackagePrice()).isEqualTo(1200000L);
        assertThat(projection.acceptedDepositAmount()).isEqualTo(600000L);
        assertThat(projection.frozenRemainingAmount()).isEqualTo(600000L);
        assertThat(projection.participantCount()).isEqualTo(2);

        // Record final balance resolved
        FinalBalanceResolutionOutcome outcome = bookingFacade.recordFinalBalanceResolved(
                new RecordFinalBalanceResolvedCommand(bookingCode, "SETTLE-99", "OP-SETTLE", "CORR-SETTLE"));
        assertThat(outcome.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);

        WorkshopBooking fullyPaid = bookingRepository.findByBookingCode(bookingCode).orElseThrow();
        assertThat(fullyPaid.getPaymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);

        // Idempotent repeat
        FinalBalanceResolutionOutcome repeat = bookingFacade.recordFinalBalanceResolved(
                new RecordFinalBalanceResolvedCommand(bookingCode, "SETTLE-99", "OP-SETTLE-REP", "CORR-SETTLE-REP"));
        assertThat(repeat.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
    }

    @Test
    @DisplayName("T007-11: Ticket redelivery creates immutable intent and dispatches post-commit handoff")
    void verifyTicketRedeliveryFlow() {
        String bookingCode = "BK-REDELIV-" + UUID.randomUUID().toString().substring(0, 8);
        UUID holdUuid = UUID.randomUUID();

        SaleEligibilitySnapshot eligibleSnap = new SaleEligibilitySnapshot(
                true, "PUBLISHED", testLocationId, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any())).thenReturn(eligibleSnap);
        when(mockOperationsFacade.reserveBookingCapacity(any())).thenReturn(
                CapacityHoldOutcome.accepted(List.of(holdUuid), Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(mockOperationsFacade.commitBookingHolds(any())).thenReturn(CapacityCommitOutcome.accepted());
        when(mockOperationsFacade.getBookingTicketProjection(any())).thenReturn(new DeliverySafeTicketSet(List.of()));

        bookingLifecycleService.createPendingBookingReservation(
                bookingCode, 1L, "Pkg", "redeliv@mland.vn", "090", 1L, 1, List.of(1101L), 300000L, 300000L, "OP", "CORR");
        bookingLifecycleService.confirmBookingPaymentAndCommitHolds(
                bookingCode, "EVT-RED-" + UUID.randomUUID(), "VNPAY", 150000L, "HASH", "OP", "CORR");

        testNotificationListener.clear();

        // Request redelivery
        TicketRedeliveryOutcome outcome = bookingFacade.requestBookingTicketRedelivery(
                new RequestBookingTicketRedeliveryCommand(bookingCode, List.of("TK-" + bookingCode + "-1"), "OP-RED-1", "CORR-RED-1"));
        assertThat(outcome.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);

        // Handoff event received
        assertThat(testNotificationListener.getReceivedEvents()).hasSize(1);
        BookingNotificationReady ready = testNotificationListener.getReceivedEvents().get(0);
        assertThat(ready.bookingReference()).isEqualTo(bookingCode);
        assertThat(ready.notificationKind()).isEqualTo("CONFIRMATION_RESEND");

        // Payload query
        BookingNotificationDeliveryPayload payload = bookingFacade.getBookingNotificationDeliveryPayload(
                new BookingNotificationDeliveryPayloadQuery(ready.notificationReference()));
        assertThat(payload).isNotNull();
        assertThat(payload.notificationKind()).isEqualTo("CONFIRMATION_RESEND");
        assertThat(payload.storedRecipient()).isEqualTo("redeliv@mland.vn");
    }

    // =========================================================================
    // Test Notification Event Listener Component
    // =========================================================================

    @TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public TestNotificationEventListener testNotificationEventListener() {
            return new TestNotificationEventListener();
        }
    }

    @Component
    public static class TestNotificationEventListener {
        private final List<BookingNotificationReady> receivedEvents = new CopyOnWriteArrayList<>();
        private final AtomicBoolean simulateDeliveryFailure = new AtomicBoolean(false);

        @EventListener
        public void onBookingNotificationReady(BookingNotificationReady event) {
            receivedEvents.add(event);
            if (simulateDeliveryFailure.get()) {
                throw new RuntimeException("Simulated Feature 007 notification delivery failure");
            }
        }

        public List<BookingNotificationReady> getReceivedEvents() {
            return receivedEvents;
        }

        public void clear() {
            receivedEvents.clear();
        }

        public void setSimulateDeliveryFailure(boolean fail) {
            simulateDeliveryFailure.set(fail);
        }
    }
}
