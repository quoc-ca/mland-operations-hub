package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLifecycleService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class WorkshopBookingDepositEvidenceIT {

    @Autowired private WorkshopBookingFacade bookingFacade;
    @Autowired private BookingLifecycleService lifecycleService;
    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private BookingInvoiceRepository invoices;
    @Autowired private BookingPaymentAttemptRepository attempts;
    @Autowired private BookingPaymentReceiptRepository receipts;
    @Autowired private BookingCapacityHoldRepository holds;

    @MockBean private WorkshopOperationsFacade operations;

    @Test
    void verifiedDepositConfirmsOnceAndBindsReceiptToOwnedAttempt() {
        String bookingCode = "BK-EVIDENCE-" + UUID.randomUUID().toString().substring(0, 8);
        UUID firstHoldRef = UUID.randomUUID();
        UUID secondHoldRef = UUID.randomUUID();
        List<UUID> sessions = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(operations.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(
                true, "PUBLISHED", 1L, Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(3600), 10));
        when(operations.reserveBookingCapacity(any())).thenReturn(CapacityHoldOutcome.accepted(
                List.of(firstHoldRef, secondHoldRef), Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(operations.commitBookingHolds(any())).thenReturn(CapacityCommitOutcome.accepted());
        List<SessionEntitlementProjection> sessionEntitlements = List.of(
                new SessionEntitlementProjection(sessions.get(0), 1L, Instant.now().plus(1, ChronoUnit.DAYS),
                        Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(3600)),
                new SessionEntitlementProjection(sessions.get(1), 1L, Instant.now().plus(2, ChronoUnit.DAYS),
                        Instant.now().plus(2, ChronoUnit.DAYS).plusSeconds(3600)));
        when(operations.getBookingTicketProjection(any())).thenReturn(new DeliverySafeTicketSet(List.of(
                new DeliverySafeTicket(UUID.randomUUID(), "QR-EVIDENCE-1", "TK-EVIDENCE-1", sessionEntitlements))));

        WorkshopBooking booking = lifecycleService.createPendingBookingReservation(
                bookingCode, 1L, "Test package", "buyer@example.test", "0900000000", 7L,
                1, sessions, 100_000L, 100_000L, "OP-RES-" + bookingCode,
                "CORR-RES-" + bookingCode);
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
        String evidenceRef = "VNPAY-EVT-" + UUID.randomUUID();
        ApplyVerifiedDepositPaymentEvidenceCommand command = new ApplyVerifiedDepositPaymentEvidenceCommand(
                bookingCode, evidenceRef, attempt.getMerchantReference(), invoice.getDepositAmount(),
                "VND", "OP-EVIDENCE-" + bookingCode, "CORR-EVIDENCE-" + bookingCode);

        DepositPaymentEvidenceOutcome accepted = bookingFacade.applyVerifiedDepositPaymentEvidence(command);

        assertThat(accepted.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(bookings.findByBookingCode(bookingCode).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(holds.findByBookingId(booking.getId())).hasSize(2)
                .allSatisfy(hold -> assertThat(hold.getHoldStatus()).isEqualTo("COMMITTED"));
        BookingPaymentReceipt receipt = receipts.findByProviderEventId(evidenceRef).orElseThrow();
        assertThat(receipt.getBooking().getId()).isEqualTo(booking.getId());
        assertThat(receipt.getAmountReceived()).isEqualTo(invoice.getDepositAmount());
        assertThat(receipt.getPaymentAttempt().getId()).isEqualTo(attempt.getId());
        assertThat(attempts.findByMerchantReference(attempt.getMerchantReference()).orElseThrow().getStatus())
                .isEqualTo(PaymentAttemptStatus.SUCCESS);

        DepositPaymentEvidenceOutcome replay = bookingFacade.applyVerifiedDepositPaymentEvidence(command);
        assertThat(replay.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(receipts.findByBookingId(booking.getId())).hasSize(1);

        DepositPaymentEvidenceOutcome unrelatedEvent = bookingFacade.applyVerifiedDepositPaymentEvidence(
                new ApplyVerifiedDepositPaymentEvidenceCommand(bookingCode,
                        "VNPAY-EVT-OTHER-" + UUID.randomUUID(), attempt.getMerchantReference(),
                        invoice.getDepositAmount(), "VND", "OP-OTHER", "CORR-OTHER"));
        assertThat(unrelatedEvent.reason()).isEqualTo(BookingRejectionReason.PAYMENT_MISMATCH);
        assertThat(receipts.findByBookingId(booking.getId())).hasSize(1);
        verify(operations, times(1)).commitBookingHolds(any());
        verify(operations, times(1)).getBookingTicketProjection(any());
    }
}
