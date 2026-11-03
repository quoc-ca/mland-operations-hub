package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FinalPaymentService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementFacadeImpl;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkshopSettlementEvidenceConsumerTest {
    private final SettlementCancellationCaseRepository cases = mock(SettlementCancellationCaseRepository.class);
    private final SettlementHandoffIdempotencyRepository handoffs = mock(SettlementHandoffIdempotencyRepository.class);
    private final SettlementEvidenceConclusionRepository evidence = mock(SettlementEvidenceConclusionRepository.class);
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final SettlementChargeRepository charges = mock(SettlementChargeRepository.class);
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final FinalPaymentService finalPayments = new FinalPaymentService(evidence, bills, charges, booking);
    private final WorkshopSettlementFacadeImpl facade = new WorkshopSettlementFacadeImpl(cases, handoffs,
            new ObjectMapper(), finalPayments, mock(SettlementNotificationHandoffRepository.class));

    @Test void acceptsAndRetainsOnlyVerifiedFinalPaymentEvidenceWithinFrozenOutstandingAmount() {
        when(evidence.findByOperationReference("OP-1")).thenReturn(Optional.empty());
        when(evidence.findByEvidenceReference("EV-1")).thenReturn(Optional.empty());
        when(bills.findByBookingReferenceForUpdate("BK-1")).thenReturn(Optional.of(openBill()));
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc("BILL-1", "CONFIRMED")).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());
        when(booking.recordFinalBalanceResolved("BK-1", "BILL-1", "OP-1", "CORR-1"))
                .thenReturn(FinalBalanceResolutionOutcome.accepted());
        when(evidence.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SettlementPaymentEvidenceOutcome result = facade.applyVerifiedSettlementPaymentEvidence(command("FINAL_PAYMENT", 75_000L));

        assertThat(result.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.ACCEPTED);
        verify(booking).recordFinalBalanceResolved("BK-1", "BILL-1", "OP-1", "CORR-1");
        verify(evidence).saveAndFlush(argThat(row -> row.getConclusion().equals("FINAL_PAYMENT_VERIFIED")
                && row.getAmountVnd().equals(75_000L) && row.getCurrencyCode().equals("VND")));
    }

    @Test void rejectsMismatchWithoutPersistingEvidence() {
        when(evidence.findByOperationReference("OP-1")).thenReturn(Optional.empty());
        when(evidence.findByEvidenceReference("EV-1")).thenReturn(Optional.empty());
        when(bills.findByBookingReferenceForUpdate("BK-1")).thenReturn(Optional.of(openBill()));
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc("BILL-1", "CONFIRMED")).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());

        SettlementPaymentEvidenceOutcome result = facade.applyVerifiedSettlementPaymentEvidence(command("FINAL_PAYMENT", 75_001L));

        assertThat(result.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.REJECTED);
        assertThat(result.reason()).isEqualTo("AMOUNT_MISMATCH");
        verifyNoInteractions(booking);
        verify(evidence, never()).saveAndFlush(any());
    }

    @Test void doesNotRetainFinalPaymentWhenBookingRejectsFinalBalanceConclusion() {
        when(evidence.findByOperationReference("OP-1")).thenReturn(Optional.empty());
        when(evidence.findByEvidenceReference("EV-1")).thenReturn(Optional.empty());
        when(bills.findByBookingReferenceForUpdate("BK-1")).thenReturn(Optional.of(openBill()));
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc("BILL-1", "CONFIRMED")).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());
        when(booking.recordFinalBalanceResolved("BK-1", "BILL-1", "OP-1", "CORR-1"))
                .thenReturn(FinalBalanceResolutionOutcome.rejected(
                        edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason.PAYMENT_MISMATCH));

        SettlementPaymentEvidenceOutcome result = facade.applyVerifiedSettlementPaymentEvidence(command("FINAL_PAYMENT", 75_000L));

        assertThat(result.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.REJECTED);
        assertThat(result.reason()).isEqualTo("PAYMENT_MISMATCH");
        verify(evidence, never()).saveAndFlush(any());
    }

    @Test void acceptsRefundOnlyUpToPreviouslyCollectedFunds() {
        when(evidence.findByOperationReference("OP-1")).thenReturn(Optional.empty());
        when(evidence.findByEvidenceReference("EV-1")).thenReturn(Optional.empty());
        when(bills.findByBookingReferenceForUpdate("BK-1")).thenReturn(Optional.of(openBill()));
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "REFUND_VERIFIED")).thenReturn(List.of());
        when(evidence.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SettlementPaymentEvidenceOutcome accepted = facade.applyVerifiedSettlementPaymentEvidence(command("REFUND", 25_000L));
        SettlementPaymentEvidenceOutcome rejected = facade.applyVerifiedSettlementPaymentEvidence(command("REFUND", 25_001L));

        assertThat(accepted.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.ACCEPTED);
        assertThat(rejected.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.REJECTED);
        assertThat(rejected.reason()).isEqualTo("AMOUNT_MISMATCH");
        verify(evidence, times(1)).saveAndFlush(any());
    }

    @Test void exactReplayOfRetainedEvidenceIsIdempotentlyAccepted() {
        var existing = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion.builder()
                .evidenceReference("EV-1").bookingReference("BK-1").providerTransactionReference("FINAL-BK-1-1")
                .conclusion("FINAL_PAYMENT_VERIFIED").amountVnd(75_000L).currencyCode("VND").operationReference("OP-1").build();
        when(evidence.findByOperationReference("OP-1")).thenReturn(Optional.of(existing));

        SettlementPaymentEvidenceOutcome result = facade.applyVerifiedSettlementPaymentEvidence(command("FINAL_PAYMENT", 75_000L));

        assertThat(result.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.ACCEPTED);
        verifyNoInteractions(bills, charges);
        verify(evidence, never()).saveAndFlush(any());
    }

    private static SettlementFinalBill openBill() {
        return SettlementFinalBill.builder().billReference("BILL-1").bookingReference("BK-1")
                .remainingAmountVnd(75_000L).acceptedDepositAmountVnd(25_000L)
                .currencyCode("VND").lifecycleStatus("OPEN").build();
    }
    private static ApplyVerifiedSettlementPaymentEvidenceCommand command(String kind, long amount) {
        return new ApplyVerifiedSettlementPaymentEvidenceCommand("BK-1", "EV-1", "FINAL-BK-1-1", kind,
                amount, "VND", "12345678", "20261030181359", "OP-1", "CORR-1");
    }
}
