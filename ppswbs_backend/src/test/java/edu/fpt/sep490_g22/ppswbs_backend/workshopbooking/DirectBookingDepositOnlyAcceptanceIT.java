package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBookingConfirmedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLifecycleService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 003.1 T087 MySQL acceptance: a direct Guest/Member booking collects only the frozen
 * deposit, keeps one immutable receipt, routes add-ons/balance/finalization to 003.3,
 * and an ordinary cancellation forfeits the deposit without any VNPay QueryDR/refund.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@RecordApplicationEvents
class DirectBookingDepositOnlyAcceptanceIT {

    private static final long ODD_POST_PROMOTION_AMOUNT = 100_001L;
    private static final long FROZEN_DEPOSIT = 50_001L;

    @Autowired private WorkshopBookingFacade bookingFacade;
    @Autowired private BookingLifecycleService lifecycleService;
    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private BookingInvoiceRepository invoices;
    @Autowired private BookingPaymentAttemptRepository attempts;
    @Autowired private BookingPaymentReceiptRepository receipts;
    @Autowired private RequestMappingHandlerMapping handlerMapping;
    @Autowired private ApplicationEvents events;

    @MockitoBean private WorkshopOperationsFacade operations;
    @MockitoBean private VnpayMerchantOperationsPort merchantOperations;

    /** Session of the booking being confirmed; the ticket projection must name the booked session. */
    private UUID currentSession;

    @BeforeEach
    void operationsAccept() {
        when(operations.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(
                true, "PUBLISHED", 1L, Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(3600), 10));
        when(operations.reserveBookingCapacity(any())).thenAnswer(invocation -> CapacityHoldOutcome.accepted(
                List.of(UUID.randomUUID()), Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(operations.commitBookingHolds(any())).thenReturn(CapacityCommitOutcome.accepted());
        when(operations.getBookingTicketProjection(any())).thenAnswer(invocation -> new DeliverySafeTicketSet(List.of(
                new DeliverySafeTicket(UUID.randomUUID(), "QR-T087-" + UUID.randomUUID(),
                        "TK-T087-" + UUID.randomUUID().toString().substring(0, 12),
                        List.of(new SessionEntitlementProjection(currentSession, 1L,
                                Instant.now().plus(1, ChronoUnit.DAYS),
                                Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(3600)))))));
    }

    @Test
    void acceptanceFreezesHalfRoundedUpDepositAndRejectsAFullAmountPayment() {
        Pending pending = reservePendingBooking();

        assertThat(pending.invoice().getPostPromotionAmount()).isEqualTo(ODD_POST_PROMOTION_AMOUNT);
        assertThat(pending.invoice().getDepositAmount()).isEqualTo(FROZEN_DEPOSIT);
        assertThat(pending.invoice().getRemainingAmount()).isEqualTo(ODD_POST_PROMOTION_AMOUNT - FROZEN_DEPOSIT);

        DepositPaymentEvidenceOutcome fullAmount = bookingFacade.applyVerifiedDepositPaymentEvidence(
                evidence(pending, "VNPAY-FULL-" + UUID.randomUUID(), ODD_POST_PROMOTION_AMOUNT));

        assertThat(fullAmount.status()).isNotEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(bookings.findByBookingCode(pending.bookingCode()).orElseThrow().getBookingStatus())
                .isNotEqualTo(BookingStatus.CONFIRMED);
        assertThat(receipts.findByBookingId(pending.booking().getId())).isEmpty();
        verify(operations, never()).commitBookingHolds(any());
    }

    @Test
    void verifiedDepositConfirmsOnceWithOneImmutableReceiptAndIdempotentReplay() {
        Pending pending = reservePendingBooking();
        String evidenceRef = "VNPAY-DEP-" + UUID.randomUUID();
        ApplyVerifiedDepositPaymentEvidenceCommand command = evidence(pending, evidenceRef, FROZEN_DEPOSIT);

        DepositPaymentEvidenceOutcome accepted = bookingFacade.applyVerifiedDepositPaymentEvidence(command);
        DepositPaymentEvidenceOutcome replay = bookingFacade.applyVerifiedDepositPaymentEvidence(command);

        assertThat(accepted.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(replay.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(bookings.findByBookingCode(pending.bookingCode()).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        List<BookingPaymentReceipt> stored = receipts.findByBookingId(pending.booking().getId());
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getProviderEventId()).isEqualTo(evidenceRef);
        assertThat(stored.get(0).getAmountReceived()).isEqualTo(FROZEN_DEPOSIT);
        assertThat(attempts.findByMerchantReference(pending.attempt().getMerchantReference()).orElseThrow()
                .getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        // One confirmation hand-off opens the single 003.3 folio; the replay publishes nothing new.
        assertThat(events.stream(MlandBookingConfirmedEvent.class)
                .filter(event -> event.bookingReference().equals(pending.bookingCode()))).hasSize(1);
        verify(operations, times(1)).commitBookingHolds(any());

        // The verified deposit is the only collection Booking owns; the balance stays with 003.3.
        VerifiedBookingDepositPaymentsProjection deposits = bookingFacade.getVerifiedDepositPayments(
                new VerifiedBookingDepositPaymentsQuery(pending.bookingCode()));
        assertThat(deposits.payments()).singleElement().satisfies(payment -> {
            assertThat(payment.amountVnd()).isEqualTo(FROZEN_DEPOSIT);
            assertThat(payment.reference()).isEqualTo(pending.attempt().getMerchantReference());
            assertThat(payment.providerTransactionNumber()).isEqualTo(evidenceRef.substring("VNPAY-".length()));
        });
        verifyNoInteractions(merchantOperations);
    }

    @Test
    void ordinaryCancellationForfeitsTheDepositWithoutQueryDrOrRefund() {
        Pending pending = reservePendingBooking();
        bookingFacade.applyVerifiedDepositPaymentEvidence(
                evidence(pending, "VNPAY-DEP-" + UUID.randomUUID(), FROZEN_DEPOSIT));
        CancelConfirmedBookingCommand cancel = new CancelConfirmedBookingCommand(pending.bookingCode(),
                "SETTLE-CANCEL-" + UUID.randomUUID(), "OP-CANCEL-" + pending.bookingCode(),
                "CORR-CANCEL-" + pending.bookingCode());

        BookingCancellationOutcome cancelled = bookingFacade.cancelConfirmedBooking(cancel);
        BookingCancellationOutcome replay = bookingFacade.cancelConfirmedBooking(cancel);

        assertThat(cancelled.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(replay.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(bookings.findByBookingCode(pending.bookingCode()).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(receipts.findByBookingId(pending.booking().getId())).hasSize(1);
        verifyNoInteractions(merchantOperations);
    }

    @Test
    void settlementConclusionStatusesPersistInTheMySqlStatusColumns() {
        // 003.1 T088 / V95: NO_SHOW, COMPLETED, REFUND_PENDING and REFUNDED must be storable.
        Pending noShow = reservePendingBooking();
        bookingFacade.applyVerifiedDepositPaymentEvidence(
                evidence(noShow, "VNPAY-DEP-" + UUID.randomUUID(), FROZEN_DEPOSIT));
        assertThat(bookingFacade.recordBookingServiceOutcome(new RecordBookingServiceOutcomeCommand(
                noShow.bookingCode(), BookingServiceOutcome.NO_SHOW, "FOLIO-" + noShow.bookingCode(),
                "OP-NOSHOW-" + noShow.bookingCode(), "CORR-NOSHOW")).status())
                .isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(bookings.findByBookingCode(noShow.bookingCode()).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.NO_SHOW);

        Pending completed = reservePendingBooking();
        bookingFacade.applyVerifiedDepositPaymentEvidence(
                evidence(completed, "VNPAY-DEP-" + UUID.randomUUID(), FROZEN_DEPOSIT));
        assertThat(bookingFacade.recordBookingServiceOutcome(new RecordBookingServiceOutcomeCommand(
                completed.bookingCode(), BookingServiceOutcome.COMPLETED, "FOLIO-" + completed.bookingCode(),
                "OP-DONE-" + completed.bookingCode(), "CORR-DONE")).status())
                .isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(bookings.findByBookingCode(completed.bookingCode()).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.COMPLETED);

        Pending refunded = reservePendingBooking();
        String queryDrTxn = "14" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000L);
        bookingFacade.applyVerifiedDepositPaymentEvidence(
                evidence(refunded, "VNPAY-QDR-" + queryDrTxn, FROZEN_DEPOSIT));
        // QueryDR-recovered deposits expose the bare provider transaction number a refund needs.
        assertThat(bookingFacade.getVerifiedDepositPayments(new VerifiedBookingDepositPaymentsQuery(
                refunded.bookingCode())).payments()).singleElement()
                .satisfies(payment -> assertThat(payment.providerTransactionNumber()).isEqualTo(queryDrTxn));
        for (BookingRefundStatus status : BookingRefundStatus.values()) {
            assertThat(bookingFacade.recordBookingRefundStatus(new RecordBookingRefundStatusCommand(
                    refunded.bookingCode(), status, "SETTLE-MLAND-" + refunded.bookingCode(),
                    "OP-" + status + "-" + refunded.bookingCode(), "CORR-" + status)).status())
                    .isEqualTo(BookingOutcomeStatus.ACCEPTED);
        }
        assertThat(bookings.findByBookingCode(refunded.bookingCode()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void bookingExposesNoAddOnBalanceOrFullPaymentRoute() {
        List<String> bookingRoutes = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> isBookingController(entry.getValue()))
                .flatMap(entry -> entry.getKey().getPatternValues().stream())
                .toList();

        assertThat(bookingRoutes).isNotEmpty()
                .contains("/api/v1/workshop-bookings/{bookingCode}/payment-attempts")
                .noneMatch(route -> route.matches("(?i).*(add-?on|balance|final|folio|full).*"));
    }

    private static boolean isBookingController(HandlerMethod handler) {
        return handler.getBeanType().getPackageName()
                .startsWith("edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking");
    }

    private Pending reservePendingBooking() {
        String bookingCode = "BK-T087-" + UUID.randomUUID().toString().substring(0, 8);
        currentSession = UUID.randomUUID();
        WorkshopBooking booking = lifecycleService.createPendingBookingReservation(
                bookingCode, 1L, "Test package", "buyer@example.test", "0900000000", null,
                1, List.of(currentSession), ODD_POST_PROMOTION_AMOUNT, ODD_POST_PROMOTION_AMOUNT,
                "OP-RES-" + bookingCode, "CORR-RES-" + bookingCode);
        BookingInvoice invoice = invoices.findByBookingId(booking.getId()).orElseThrow();
        BookingPaymentAttempt attempt = attempts.save(BookingPaymentAttempt.builder()
                .booking(booking)
                .invoice(invoice)
                .attemptReference("ATT-" + UUID.randomUUID())
                .merchantReference("DEP-" + bookingCode + "-" + UUID.randomUUID())
                .attemptAmount(invoice.getDepositAmount())
                .status(PaymentAttemptStatus.PENDING)
                .expiresAt(booking.getHoldExpiresAt())
                .build());
        return new Pending(bookingCode, booking, invoice, attempt);
    }

    private static ApplyVerifiedDepositPaymentEvidenceCommand evidence(Pending pending, String evidenceRef, long amount) {
        return new ApplyVerifiedDepositPaymentEvidenceCommand(pending.bookingCode(), evidenceRef,
                pending.attempt().getMerchantReference(), amount, "VND",
                "OP-EVIDENCE-" + evidenceRef, "CORR-EVIDENCE-" + pending.bookingCode());
    }

    private record Pending(String bookingCode, WorkshopBooking booking, BookingInvoice invoice,
                           BookingPaymentAttempt attempt) { }
}
