package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingNotificationIntentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingPaymentReceipt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingInvoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ApplyVerifiedDepositPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacadeImpl;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkshopBookingFacadePaymentAttemptTest {

    @Mock private WorkshopBookingRepository bookingRepository;
    @Mock private BookingInvoiceRepository invoiceRepository;
    @Mock private BookingSessionCommitmentRepository commitmentRepository;
    @Mock private BookingNotificationIntentRepository notificationIntentRepository;
    @Mock private BookingAuditEvidenceRepository auditEvidenceRepository;
    @Mock private BookingPaymentAttemptRepository paymentAttemptRepository;
    @Mock private BookingPaymentReceiptRepository paymentReceiptRepository;
    @Mock private BookingLifecycleService bookingLifecycleService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private WorkshopBookingFacadeImpl facade;

    @Test
    void acceptedEvidenceConfirmsOnlyWhenBookingInvoiceAndAttemptAmountsMatch() {
        WorkshopBooking booking = WorkshopBooking.builder()
                .id(1L)
                .bookingCode("BK-1")
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .holdExpiresAt(Instant.now().plusSeconds(60))
                .build();
        BookingInvoice invoice = BookingInvoice.builder()
                .id(2L).booking(booking).depositAmount(50_000L).currency("VND").build();
        BookingPaymentAttempt attempt = BookingPaymentAttempt.builder()
                .id(3L)
                .booking(booking)
                .invoice(invoice)
                .attemptAmount(50_000L)
                .attemptReference("ATT-1")
                .merchantReference("DEP-BK-1-1")
                .status(PaymentAttemptStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        BookingPaymentReceipt receipt = BookingPaymentReceipt.builder()
                .booking(booking).providerEventId("VNPAY-1").amountReceived(50_000L).build();
        when(bookingRepository.findLockedByBookingCode("BK-1")).thenReturn(Optional.of(booking));
        when(invoiceRepository.findByBookingId(1L)).thenReturn(Optional.of(invoice));
        when(paymentAttemptRepository.findByMerchantReference("DEP-BK-1-1")).thenReturn(Optional.of(attempt));
        when(paymentReceiptRepository.findByProviderEventId("VNPAY-1")).thenReturn(Optional.of(receipt));
        when(bookingLifecycleService.confirmBookingPaymentAndCommitHolds(
                "BK-1", "VNPAY-1", "VERIFIED_EVIDENCE", 50_000L,
                "PAYLOAD_HASH_VNPAY-1", "VNPAY-1", "VNPAY-1")).thenReturn(booking);

        DepositPaymentEvidenceOutcome outcome = facade.applyVerifiedDepositPaymentEvidence(
                new ApplyVerifiedDepositPaymentEvidenceCommand(
                        "BK-1", "VNPAY-1", "DEP-BK-1-1", 50_000L, "VND", "VNPAY-1", "VNPAY-1"));

        assertThat(outcome.status()).hasToString("ACCEPTED");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(receipt.getPaymentAttempt()).isSameAs(attempt);
        verify(bookingLifecycleService).confirmBookingPaymentAndCommitHolds(
                "BK-1", "VNPAY-1", "VERIFIED_EVIDENCE", 50_000L,
                "PAYLOAD_HASH_VNPAY-1", "VNPAY-1", "VNPAY-1");
        verify(paymentReceiptRepository).save(receipt);
        verify(paymentAttemptRepository).save(attempt);
    }

    @Test
    void amountMismatchRejectsEvidenceWithoutConfirmingBooking() {
        WorkshopBooking booking = WorkshopBooking.builder().id(11L).bookingCode("BK-AMT")
                .bookingStatus(BookingStatus.PENDING_PAYMENT).build();
        BookingInvoice invoice = BookingInvoice.builder().id(12L).booking(booking)
                .depositAmount(50_000L).currency("VND").build();
        BookingPaymentAttempt attempt = BookingPaymentAttempt.builder().id(13L).booking(booking)
                .invoice(invoice).attemptAmount(50_000L).merchantReference("DEP-BK-AMT")
                .status(PaymentAttemptStatus.PENDING).build();
        when(bookingRepository.findLockedByBookingCode("BK-AMT")).thenReturn(Optional.of(booking));
        when(invoiceRepository.findByBookingId(11L)).thenReturn(Optional.of(invoice));
        when(paymentAttemptRepository.findByMerchantReference("DEP-BK-AMT")).thenReturn(Optional.of(attempt));

        DepositPaymentEvidenceOutcome outcome = facade.applyVerifiedDepositPaymentEvidence(
                new ApplyVerifiedDepositPaymentEvidenceCommand(
                        "BK-AMT", "EVT-AMT", "DEP-BK-AMT", 49_900L, "VND", "OP-AMT", "CORR-AMT"));

        assertThat(outcome.reason()).hasToString("PAYMENT_MISMATCH");
        verify(bookingLifecycleService, never()).confirmBookingPaymentAndCommitHolds(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void confirmedBookingOnlyAcceptsReplayForItsExistingReceiptAndAttempt() {
        WorkshopBooking booking = WorkshopBooking.builder().id(21L).bookingCode("BK-REPLAY")
                .bookingStatus(BookingStatus.CONFIRMED).build();
        BookingInvoice invoice = BookingInvoice.builder().id(22L).booking(booking)
                .depositAmount(50_000L).currency("VND").build();
        BookingPaymentAttempt attempt = BookingPaymentAttempt.builder().id(23L).booking(booking)
                .invoice(invoice).attemptAmount(50_000L).merchantReference("DEP-BK-REPLAY")
                .status(PaymentAttemptStatus.SUCCESS).build();
        when(bookingRepository.findLockedByBookingCode("BK-REPLAY")).thenReturn(Optional.of(booking));
        when(invoiceRepository.findByBookingId(21L)).thenReturn(Optional.of(invoice));
        when(paymentAttemptRepository.findByMerchantReference("DEP-BK-REPLAY")).thenReturn(Optional.of(attempt));
        when(paymentReceiptRepository.findByProviderEventId("EVT-REPLAY")).thenReturn(Optional.empty());

        DepositPaymentEvidenceOutcome outcome = facade.applyVerifiedDepositPaymentEvidence(
                new ApplyVerifiedDepositPaymentEvidenceCommand(
                        "BK-REPLAY", "EVT-REPLAY", "DEP-BK-REPLAY", 50_000L, "VND", "OP", "CORR"));

        assertThat(outcome.reason()).hasToString("PAYMENT_MISMATCH");
        verify(bookingLifecycleService, never()).confirmBookingPaymentAndCommitHolds(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void lateEvidenceDoesNotReopenAnExpiredAttempt() {
        WorkshopBooking booking = WorkshopBooking.builder()
                .id(31L)
                .bookingCode("BK-2")
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .holdExpiresAt(Instant.now().minusSeconds(1))
                .build();
        BookingInvoice invoice = BookingInvoice.builder()
                .id(32L).booking(booking).depositAmount(50_000L).currency("VND").build();
        BookingPaymentAttempt attempt = BookingPaymentAttempt.builder()
                .id(33L)
                .booking(booking)
                .invoice(invoice)
                .attemptAmount(50_000L)
                .attemptReference("ATT-2")
                .merchantReference("DEP-BK-2-1")
                .status(PaymentAttemptStatus.EXPIRED)
                .expiresAt(Instant.now().minusSeconds(1))
                .build();
        when(bookingRepository.findLockedByBookingCode("BK-2")).thenReturn(Optional.of(booking));
        when(invoiceRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(invoice));
        when(paymentAttemptRepository.findByMerchantReference("DEP-BK-2-1")).thenReturn(Optional.of(attempt));
        DepositPaymentEvidenceOutcome outcome = facade.applyVerifiedDepositPaymentEvidence(
                new ApplyVerifiedDepositPaymentEvidenceCommand(
                        "BK-2", "VNPAY-2", "DEP-BK-2-1", 50_000L, "VND", "VNPAY-2", "VNPAY-2"));

        assertThat(outcome.reason()).hasToString("HOLD_MISSING_OR_EXPIRED");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.EXPIRED);
    }
}
