package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentIngressOutcomeQueryPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingDepositReconciliationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingLatePaymentEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingDepositReconciliation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E1 (2026-11-12): an expired payment attempt that took no money gives back the Member quote's held points and voucher,
 * also when the customer released the pending hold after starting that payment (FR-017j), and also when VNPay has no
 * transaction for the attempt (QueryDR 91: the link was opened but never paid).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingDepositReconciliationServiceTest {

    private static final String CODE = "BK-E1";
    private static final String MERCHANT_REF = "MREF-E1";

    @Mock private WorkshopBookingRepository bookings;
    @Mock private BookingPaymentAttemptRepository attempts;
    @Mock private BookingDepositReconciliationRepository reconciliations;
    @Mock private BookingLatePaymentEvidenceRepository latePayments;
    @Mock private BookingHoldReleaseService holdRelease;
    @Mock private WorkshopBookingLoyaltyOutcomeService loyalty;
    @Mock private WorkshopBookingFacade bookingFacade;
    @Mock private PaymentIngressOutcomeQueryPort ingress;
    @Mock private VnpayMerchantOperationsPort merchant;
    @Mock private PlatformTransactionManager transactionManager;

    private BookingDepositReconciliationService service;
    private final Instant now = Instant.parse("2026-11-12T10:00:00Z");
    private WorkshopBooking booking;
    private BookingPaymentAttempt attempt;
    private BookingDepositReconciliation row;

    @BeforeEach
    void setUp() {
        service = new BookingDepositReconciliationService(bookings, attempts, reconciliations, latePayments,
                holdRelease, loyalty, bookingFacade, ingress, merchant, "127.0.0.1", transactionManager);
        booking = WorkshopBooking.builder().id(7L).bookingCode(CODE)
                .bookingStatus(BookingStatus.CANCELLED).paymentStatus(PaymentStatus.UNPAID).build();
        attempt = BookingPaymentAttempt.builder().id(11L).booking(booking).attemptReference("ATT-E1")
                .merchantReference(MERCHANT_REF).providerCreateDate("20261112093000").attemptAmount(150_000L)
                .status(PaymentAttemptStatus.PENDING).expiresAt(now.minus(Duration.ofMinutes(10))).build();
        row = new BookingDepositReconciliation();
        row.setAttempt(attempt);
        row.setLifecycleStatus("PENDING");
        row.setNextQueryAt(now.minusSeconds(1));

        when(attempts.findById(11L)).thenReturn(Optional.of(attempt));
        when(attempts.findByBookingIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(attempt));
        when(bookings.findLockedByBookingCode(CODE)).thenReturn(Optional.of(booking));
        when(reconciliations.findLockedByAttemptId(11L)).thenReturn(Optional.of(row));
        when(reconciliations.findByAttemptId(11L)).thenReturn(Optional.of(row));
        when(ingress.findVerifiedTerminalOutcome(MERCHANT_REF)).thenReturn(Optional.empty());
        when(latePayments.existsByBookingCode(CODE)).thenReturn(false);
    }

    @Test
    void releasedHoldWhosePaymentFailedGivesBackHeldBenefits() {
        queryDrAnswers("00", "02", 150_000L);

        service.reconcileAttempt(11L, now);

        assertThat(row.getLifecycleStatus()).isEqualTo("FAILED_VERIFIED");
        verify(loyalty).finishConfirmedUnpaidExpiry(eq(booking), anyString());
    }

    @Test
    void releasedHoldWhosePaymentLinkWasNeverPaidGivesBackHeldBenefits() {
        queryDrAnswers("91", null, null);

        service.reconcileAttempt(11L, now);

        assertThat(row.getLifecycleStatus()).isEqualTo("NOT_FOUND_VERIFIED");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        verify(loyalty).finishConfirmedUnpaidExpiry(eq(booking), anyString());
    }

    @Test
    void expiredHoldWhosePaymentLinkWasNeverPaidGivesBackHeldBenefits() {
        booking.setBookingStatus(BookingStatus.EXPIRED);
        queryDrAnswers("91", null, null);

        service.reconcileAttempt(11L, now);

        assertThat(row.getLifecycleStatus()).isEqualTo("NOT_FOUND_VERIFIED");
        verify(loyalty).finishConfirmedUnpaidExpiry(eq(booking), anyString());
    }

    @Test
    void cancelledBookingThatHadPaidItsDepositKeepsItsBenefits() {
        booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);
        queryDrAnswers("00", "02", 150_000L);

        service.reconcileAttempt(11L, now);

        assertThat(row.getLifecycleStatus()).isEqualTo("PENDING");
        verify(loyalty, never()).finishConfirmedUnpaidExpiry(any(), anyString());
    }

    @Test
    void latePaymentEvidenceLeavesBenefitsToTheLatePaymentFlow() {
        when(latePayments.existsByBookingCode(CODE)).thenReturn(true);
        queryDrAnswers("91", null, null);

        service.reconcileAttempt(11L, now);

        assertThat(row.getLifecycleStatus()).isEqualTo("PENDING");
        verify(loyalty, never()).finishConfirmedUnpaidExpiry(any(), anyString());
    }

    @Test
    void anotherAttemptStillUnresolvedKeepsBenefitsHeld() {
        BookingPaymentAttempt other = BookingPaymentAttempt.builder().id(12L).booking(booking).build();
        BookingDepositReconciliation otherRow = new BookingDepositReconciliation();
        otherRow.setLifecycleStatus("PENDING");
        when(attempts.findByBookingIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(attempt, other));
        when(reconciliations.findByAttemptId(12L)).thenReturn(Optional.of(otherRow));
        queryDrAnswers("91", null, null);

        service.reconcileAttempt(11L, now);

        assertThat(row.getLifecycleStatus()).isEqualTo("NOT_FOUND_VERIFIED");
        verify(loyalty, never()).finishConfirmedUnpaidExpiry(any(), anyString());
    }

    @Test
    void unverifiedNotFoundAnswerChangesNothing() {
        when(merchant.queryTransaction(any())).thenReturn(new VnpayMerchantOperationResult("op", "QUERYDR",
                "RECONCILED", "91", null, null, null, MERCHANT_REF, null, false));

        service.reconcileAttempt(11L, now);

        assertThat(row.getLifecycleStatus()).isEqualTo("PENDING");
        verify(loyalty, never()).finishConfirmedUnpaidExpiry(any(), anyString());
    }

    private void queryDrAnswers(String responseCode, String transactionStatus, Long amountVnd) {
        when(merchant.queryTransaction(any())).thenReturn(new VnpayMerchantOperationResult("op", "QUERYDR",
                "RECONCILED", responseCode, "01", transactionStatus, null, MERCHANT_REF, amountVnd, true));
    }
}
