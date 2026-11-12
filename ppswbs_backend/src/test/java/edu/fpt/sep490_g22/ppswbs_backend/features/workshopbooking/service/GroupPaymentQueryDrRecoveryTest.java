package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentIngressOutcomeQueryPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentRecoveryCandidate;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 003.1 T093, FR-032: the two bounded QueryDR checks of a group's single payment, at minutes 10 and 11 of
 * the 12-minute hold. Nothing here extends the hold and an unclear answer is ignored.
 */
class GroupPaymentQueryDrRecoveryTest {

    private static final String GROUP = "GRP-TEST0001";
    private static final String MERCHANT = "GRP-TEST0001-M1";
    private static final long AMOUNT = 400_000L;
    /** The hold started 12 minutes before this, so "now" sits exactly on a given minute of the hold. */
    private static final Instant HOLD_START = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant HOLD_EXPIRY = HOLD_START.plus(Duration.ofMinutes(12));

    private GroupPaymentAttemptRepository attempts;
    private PaymentIngressOutcomeQueryPort ingressOutcomes;
    private VnpayMerchantOperationsPort merchantOperations;
    private GroupPaymentEvidenceService evidence;
    private GroupPaymentQueryDrRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        attempts = mock(GroupPaymentAttemptRepository.class);
        ingressOutcomes = mock(PaymentIngressOutcomeQueryPort.class);
        merchantOperations = mock(VnpayMerchantOperationsPort.class);
        evidence = mock(GroupPaymentEvidenceService.class);
        when(evidence.apply(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(DepositPaymentEvidenceOutcome.accepted());
        scheduler = new GroupPaymentQueryDrRecoveryScheduler(attempts, ingressOutcomes, merchantOperations, evidence,
                "203.0.113.9");
    }

    @Test
    void asksNothingBeforeMinuteTen() {
        scheduler.recover(candidate(), atMinute(9));

        verifyNoInteractions(merchantOperations);
        verifyNoInteractions(evidence);
    }

    @Test
    void asksAtMinuteTenWhenTheLinkDiesAndAppliesTheVerifiedAnswer() {
        when(merchantOperations.queryTransaction(any())).thenReturn(paid());

        scheduler.recover(candidate(), atMinute(10));

        ArgumentCaptor<VnpayQueryDrCommand> query = ArgumentCaptor.forClass(VnpayQueryDrCommand.class);
        verify(merchantOperations, times(1)).queryTransaction(query.capture());
        assertThat(query.getValue().operationReference()).endsWith("-M10");
        assertThat(query.getValue().merchantReference()).isEqualTo(MERCHANT);
        assertThat(query.getValue().originalTransactionDate()).isEqualTo("20261112170000");
        verify(evidence).apply(eq(GROUP), eq(MERCHANT), eq(AMOUNT), eq("VND"), anyString(),
                eq("VNPAY-QDR-TX-7"));
    }

    @Test
    void asksBothCheckpointsAtMinuteElevenWhenTheFirstAnswerWasUnclear() {
        when(merchantOperations.queryTransaction(any())).thenReturn(pending(), paid());

        scheduler.recover(candidate(), atMinute(11));

        ArgumentCaptor<VnpayQueryDrCommand> query = ArgumentCaptor.forClass(VnpayQueryDrCommand.class);
        verify(merchantOperations, times(2)).queryTransaction(query.capture());
        assertThat(query.getAllValues()).extracting(VnpayQueryDrCommand::operationReference)
                .satisfiesExactly(first -> assertThat(first).endsWith("-M10"),
                        second -> assertThat(second).endsWith("-M11"));
        verify(evidence, times(1)).apply(eq(GROUP), eq(MERCHANT), eq(AMOUNT), eq("VND"), anyString(), anyString());
    }

    @Test
    void stopsAfterTheFirstCheckpointThatPaid() {
        when(merchantOperations.queryTransaction(any())).thenReturn(paid());

        scheduler.recover(candidate(), atMinute(11));

        verify(merchantOperations, times(1)).queryTransaction(any());
    }

    @Test
    void asksNothingOnceAVerifiedIpnIsAlreadyRecorded() {
        when(ingressOutcomes.hasVerifiedTerminalOutcome(MERCHANT)).thenReturn(true);

        scheduler.recover(candidate(), atMinute(11));

        verifyNoInteractions(merchantOperations);
        verifyNoInteractions(evidence);
    }

    @Test
    void anAnswerThatIsNotSignedSuccessfulAndExactIsNeverTreatedAsEvidence() {
        VnpayMerchantOperationResult unsigned = result("00", "00", "TX-7", AMOUNT, false);
        VnpayMerchantOperationResult failedCode = result("99", "00", "TX-7", AMOUNT, true);
        VnpayMerchantOperationResult otherAmount = result("00", "00", "TX-7", AMOUNT - 1_000, true);
        VnpayMerchantOperationResult noTransaction = result("00", "00", "  ", AMOUNT, true);
        for (VnpayMerchantOperationResult answer : new VnpayMerchantOperationResult[]{
                unsigned, failedCode, otherAmount, noTransaction, null}) {
            clearInvocations(evidence);
            when(merchantOperations.queryTransaction(any())).thenReturn(answer);

            scheduler.recover(candidate(), atMinute(10));

            verifyNoInteractions(evidence);
        }
    }

    @Test
    void anAnswerForAnotherMerchantReferenceIsIgnored() {
        when(merchantOperations.queryTransaction(any()))
                .thenReturn(new VnpayMerchantOperationResult("op", "QUERYDR", "RECONCILED", "00", "01", "00",
                        "TX-7", "SOMEONE-ELSE", AMOUNT, true));

        scheduler.recover(candidate(), atMinute(10));

        verifyNoInteractions(evidence);
    }

    @Test
    void asksNothingAfterTheHoldBecauseALatePaymentIsReviewedSeparately() {
        scheduler.recover(candidate(), HOLD_EXPIRY.plusSeconds(1));

        verifyNoInteractions(merchantOperations);
        verifyNoInteractions(evidence);
    }

    @Test
    void asksNothingWithoutTheProviderCreateDateTheQueryNeeds() {
        GroupPaymentRecoveryCandidate withoutDate = new GroupPaymentRecoveryCandidate(1L, "GATT-1", MERCHANT, AMOUNT,
                null, HOLD_EXPIRY.minus(Duration.ofMinutes(2)), GROUP, BookingStatus.PENDING_PAYMENT, HOLD_EXPIRY,
                HOLD_EXPIRY.minus(Duration.ofMinutes(2)));

        scheduler.recover(withoutDate, atMinute(10));

        verifyNoInteractions(merchantOperations);
    }

    @Test
    void asksNothingWhenTheMerchantApiAddressIsNotConfigured() {
        GroupPaymentQueryDrRecoveryScheduler unconfigured = new GroupPaymentQueryDrRecoveryScheduler(attempts,
                ingressOutcomes, merchantOperations, evidence, "   ");

        unconfigured.recover(candidate(), atMinute(11));

        verifyNoInteractions(merchantOperations);
        verifyNoInteractions(ingressOutcomes);
    }

    @Test
    void aRejectedApplicationIsNotRetriedAtTheNextCheckpoint() {
        when(merchantOperations.queryTransaction(any())).thenReturn(paid());
        when(evidence.apply(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY));

        scheduler.recover(candidate(), atMinute(11));

        verify(merchantOperations, times(1)).queryTransaction(any());
        verify(evidence, times(1)).apply(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void theSweepSurvivesOneFailingCandidate() {
        when(attempts.findRecoveryCandidates(any(), eq(BookingStatus.PENDING_PAYMENT)))
                .thenReturn(java.util.List.of(candidate(), candidate()));
        when(merchantOperations.queryTransaction(any())).thenThrow(new IllegalStateException("gateway down"));

        scheduler.recoverEligibleGroupPayments();

        // Both candidates were attempted even though the first one blew up; nothing was applied.
        verifyNoInteractions(evidence);
    }

    // ---- helpers ----

    /** "now" sitting exactly on the given minute of the 12-minute hold. */
    private static Instant atMinute(int minute) {
        return HOLD_START.plus(Duration.ofMinutes(minute));
    }

    private static GroupPaymentRecoveryCandidate candidate() {
        return new GroupPaymentRecoveryCandidate(1L, "GATT-1", MERCHANT, AMOUNT, "20261112170000",
                HOLD_EXPIRY.minus(Duration.ofMinutes(2)), GROUP, BookingStatus.PENDING_PAYMENT, HOLD_EXPIRY,
                HOLD_EXPIRY.minus(Duration.ofMinutes(2)));
    }

    private static VnpayMerchantOperationResult paid() {
        return result("00", "00", "TX-7", AMOUNT, true);
    }

    private static VnpayMerchantOperationResult pending() {
        return result("00", "01", "TX-7", AMOUNT, true);
    }

    private static VnpayMerchantOperationResult result(String responseCode, String transactionStatus,
                                                       String providerTransaction, long amount, boolean verified) {
        return new VnpayMerchantOperationResult("op", "QUERYDR", "RECONCILED", responseCode, "01",
                transactionStatus, providerTransaction, MERCHANT, amount, verified);
    }
}
