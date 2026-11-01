package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentIngressOutcomeQueryPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.DepositPaymentRecoveryCandidate;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ApplyVerifiedDepositPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordLateDepositEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositPaymentQueryDrRecoverySchedulerTest {
    @Mock private BookingPaymentAttemptRepository attempts;
    @Mock private PaymentIngressOutcomeQueryPort ingressOutcomes;
    @Mock private VnpayMerchantOperationsPort merchantOperations;
    @Mock private WorkshopBookingFacade booking;

    @Test
    void queriesOnlyAtMinuteEightAndNineAndUsesDistinctOperationReferences() {
        var scheduler = scheduler();
        Instant base = Instant.parse("2026-10-31T10:00:00Z");
        when(ingressOutcomes.hasVerifiedTerminalOutcome("DEP-BK-1-ATT-1")).thenReturn(false);
        when(merchantOperations.queryTransaction(any())).thenReturn(null);

        scheduler.recover(candidate(base), base.plusSeconds(7 * 60L + 59));
        verifyNoInteractions(merchantOperations);

        scheduler.recover(candidate(base), base.plusSeconds(8 * 60L));
        var first = org.mockito.ArgumentCaptor.forClass(VnpayQueryDrCommand.class);
        verify(merchantOperations).queryTransaction(first.capture());
        org.assertj.core.api.Assertions.assertThat(first.getValue().operationReference()).isEqualTo("QDR-ATT-1-M8");
        org.assertj.core.api.Assertions.assertThat(first.getValue().ipAddress()).isEqualTo("203.0.113.10");

        clearInvocations(merchantOperations);
        scheduler.recover(candidate(base), base.plusSeconds(9 * 60L));
        var later = org.mockito.ArgumentCaptor.forClass(VnpayQueryDrCommand.class);
        verify(merchantOperations, times(2)).queryTransaction(later.capture());
        org.assertj.core.api.Assertions.assertThat(later.getAllValues()).extracting(VnpayQueryDrCommand::operationReference)
                .containsExactly("QDR-ATT-1-M8", "QDR-ATT-1-M9");
        verify(booking, never()).applyVerifiedDepositPaymentEvidence(any());
    }

    @Test
    void doesNotQueryWhenHoldExpiredOrAValidTerminalIpnExists() {
        var scheduler = scheduler();
        Instant base = Instant.parse("2026-10-31T10:00:00Z");
        scheduler.recover(candidate(base), base.plusSeconds(10 * 60L));
        verifyNoInteractions(ingressOutcomes, merchantOperations);

        when(ingressOutcomes.hasVerifiedTerminalOutcome("DEP-BK-1-ATT-1")).thenReturn(true);
        scheduler.recover(candidate(base), base.plusSeconds(9 * 60L));
        verify(ingressOutcomes).hasVerifiedTerminalOutcome("DEP-BK-1-ATT-1");
        verifyNoInteractions(merchantOperations);
    }

    @Test
    void onlyMatchingSignatureVerifiedSuccessCanReachBookingFacade() {
        var scheduler = scheduler();
        Instant base = Instant.parse("2026-10-31T10:00:00Z");
        when(ingressOutcomes.hasVerifiedTerminalOutcome("DEP-BK-1-ATT-1")).thenReturn(false);
        when(merchantOperations.queryTransaction(any())).thenReturn(
                result(false, "DEP-BK-1-ATT-1", 50_000L),
                result(true, "DEP-BK-1-ATT-1", 50_000L));
        when(booking.applyVerifiedDepositPaymentEvidence(any())).thenReturn(DepositPaymentEvidenceOutcome.accepted());

        scheduler.recover(candidate(base), base.plusSeconds(9 * 60L));

        verify(merchantOperations, times(2)).queryTransaction(any());
        verify(booking).applyVerifiedDepositPaymentEvidence(argThat(command ->
                command.bookingReference().equals("BK-QUERY-1") && command.verifiedEvidenceReference().equals("VNPAY-QDR-15720588")
                        && command.amountVnd() == 50_000L));
    }

    @Test
    void mismatchedMerchantOrAmountFromQueryDrNeverReachesBookingFacade() {
        var scheduler = scheduler();
        Instant base = Instant.parse("2026-10-31T10:00:00Z");
        when(ingressOutcomes.hasVerifiedTerminalOutcome("DEP-BK-1-ATT-1")).thenReturn(false);
        when(merchantOperations.queryTransaction(any())).thenReturn(
                result(true, "WRONG-MERCHANT", 50_000L),
                result(true, "DEP-BK-1-ATT-1", 49_900L));

        scheduler.recover(candidate(base), base.plusSeconds(9 * 60L));

        verify(merchantOperations, times(2)).queryTransaction(any());
        verifyNoInteractions(booking);
    }

    @Test
    void nonSuccessfulQueryDrOutcomesNeverReachBookingFacade() {
        var scheduler = scheduler();
        Instant base = Instant.parse("2026-10-31T10:00:00Z");
        when(ingressOutcomes.hasVerifiedTerminalOutcome("DEP-BK-1-ATT-1")).thenReturn(false);
        when(merchantOperations.queryTransaction(any())).thenReturn(
                result(true, "DEP-BK-1-ATT-1", 50_000L, "99", "00"),
                result(true, "DEP-BK-1-ATT-1", 50_000L, "00", "01"));

        scheduler.recover(candidate(base), base.plusSeconds(9 * 60L));

        verify(merchantOperations, times(2)).queryTransaction(any());
        verifyNoInteractions(booking);
    }

    @Test
    void stopsBeforeMinuteNineWhenVerifiedTerminalIpnArrivesAfterMinuteEightQuery() {
        var scheduler = scheduler();
        Instant base = Instant.parse("2026-10-31T10:00:00Z");
        when(ingressOutcomes.hasVerifiedTerminalOutcome("DEP-BK-1-ATT-1"))
                .thenReturn(false, false, true);
        when(merchantOperations.queryTransaction(any())).thenReturn(null);

        scheduler.recover(candidate(base), base.plusSeconds(9 * 60L));

        verify(merchantOperations, times(1)).queryTransaction(any());
        verifyNoInteractions(booking);
    }

    @Test
    void verifiedQueryDrSuccessRejectedForExpiredHoldIsRecordedAsLateEvidence() {
        var scheduler = scheduler();
        Instant base = Instant.parse("2026-10-31T10:00:00Z");
        when(ingressOutcomes.hasVerifiedTerminalOutcome("DEP-BK-1-ATT-1")).thenReturn(false);
        when(merchantOperations.queryTransaction(any())).thenReturn(
                result(true, "DEP-BK-1-ATT-1", 50_000L, "00", "00"));
        when(booking.applyVerifiedDepositPaymentEvidence(any())).thenReturn(
                DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.HOLD_MISSING_OR_EXPIRED));

        scheduler.recover(candidate(base), base.plusSeconds(9 * 60L));

        verify(booking).applyVerifiedDepositPaymentEvidence(any(ApplyVerifiedDepositPaymentEvidenceCommand.class));
        verify(booking).recordLateDepositEvidence(argThat((RecordLateDepositEvidenceCommand command) ->
                command.bookingReference().equals("BK-QUERY-1")
                        && command.merchantReference().equals("DEP-BK-1-ATT-1")
                        && command.providerTransactionReference().equals("15720588")
                        && command.amountVnd() == 50_000L));
    }

    private DepositPaymentRecoveryCandidate candidate(Instant base) {
        return new DepositPaymentRecoveryCandidate(1L, "ATT-1", "DEP-BK-1-ATT-1", 50_000L,
                "20261031170000", base, base.plusSeconds(600), "BK-QUERY-1", BookingStatus.PENDING_PAYMENT,
                base.plusSeconds(600));
    }

    private DepositPaymentQueryDrRecoveryScheduler scheduler() {
        return new DepositPaymentQueryDrRecoveryScheduler(attempts, ingressOutcomes, merchantOperations, booking, "203.0.113.10");
    }

    private VnpayMerchantOperationResult result(boolean signature, String merchant, long amount) {
        return result(signature, merchant, amount, "00", "00");
    }

    private VnpayMerchantOperationResult result(boolean signature, String merchant, long amount,
                                                 String responseCode, String transactionStatus) {
        return new VnpayMerchantOperationResult("QDR-1", "QUERYDR", "COMPLETED", responseCode, "02", transactionStatus,
                "15720588", merchant, amount, signature);
    }
}
