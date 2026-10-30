package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingNotificationIntentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingPaymentAttempt;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkshopBookingFacadePaymentAttemptTest {

    @Mock private WorkshopBookingRepository bookingRepository;
    @Mock private BookingInvoiceRepository invoiceRepository;
    @Mock private BookingSessionCommitmentRepository commitmentRepository;
    @Mock private BookingNotificationIntentRepository notificationIntentRepository;
    @Mock private BookingAuditEvidenceRepository auditEvidenceRepository;
    @Mock private BookingPaymentAttemptRepository paymentAttemptRepository;
    @Mock private BookingLifecycleService bookingLifecycleService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private WorkshopBookingFacadeImpl facade;

    @Test
    void acceptedEvidenceMarksTheMatchingPendingAttemptSuccessful() {
        WorkshopBooking booking = WorkshopBooking.builder()
                .bookingCode("BK-1")
                .bookingStatus(BookingStatus.CONFIRMED)
                .build();
        BookingPaymentAttempt attempt = BookingPaymentAttempt.builder()
                .attemptReference("ATT-1")
                .merchantReference("DEP-BK-1-1")
                .status(PaymentAttemptStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(bookingRepository.findByBookingCode("BK-1")).thenReturn(Optional.of(booking));
        when(paymentAttemptRepository.findByMerchantReference("DEP-BK-1-1")).thenReturn(Optional.of(attempt));

        DepositPaymentEvidenceOutcome outcome = facade.applyVerifiedDepositPaymentEvidence(
                new ApplyVerifiedDepositPaymentEvidenceCommand(
                        "BK-1", "VNPAY-1", "DEP-BK-1-1", 50_000L, "VND", "VNPAY-1", "VNPAY-1"));

        assertThat(outcome.status()).hasToString("ACCEPTED");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        verify(paymentAttemptRepository).save(attempt);
    }

    @Test
    void lateEvidenceDoesNotReopenAnExpiredAttempt() {
        WorkshopBooking booking = WorkshopBooking.builder()
                .bookingCode("BK-2")
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .holdExpiresAt(Instant.now().minusSeconds(1))
                .build();
        BookingPaymentAttempt attempt = BookingPaymentAttempt.builder()
                .attemptReference("ATT-2")
                .merchantReference("DEP-BK-2-1")
                .status(PaymentAttemptStatus.EXPIRED)
                .expiresAt(Instant.now().minusSeconds(1))
                .build();
        when(bookingRepository.findByBookingCode("BK-2")).thenReturn(Optional.of(booking));
        DepositPaymentEvidenceOutcome outcome = facade.applyVerifiedDepositPaymentEvidence(
                new ApplyVerifiedDepositPaymentEvidenceCommand(
                        "BK-2", "VNPAY-2", "DEP-BK-2-1", 50_000L, "VND", "VNPAY-2", "VNPAY-2"));

        assertThat(outcome.reason()).hasToString("HOLD_MISSING_OR_EXPIRED");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.EXPIRED);
    }
}
