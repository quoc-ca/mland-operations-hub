package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SettlementReadiness;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopLoyaltyOutcomeProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkshopLoyaltyOutcomeProcessorTest {
    @Mock SettlementFinalBillRepository bills;
    @Mock SettlementChargeRepository charges;
    @Mock SettlementEvidenceConclusionRepository evidence;
    @Mock WorkshopBookingSettlementAdapter booking;
    @Mock WorkshopOperationsSettlementAdapter operations;
    @Mock LoyaltyFacade loyalty;

    @Test
    void waitsForCorrectionWindowBeforeEmittingAnyTerminalOutcome() {
        readyBill("OPEN");
        when(operations.readiness("BK-1")).thenReturn(new SettlementReadiness(true, false, true, Instant.now()));

        processor().process("BK-1");

        verifyNoInteractions(booking, loyalty);
    }

    @Test
    void finishesEarnForNoShowAfterWindowClosesWithoutWaitingForBillPayment() {
        readyBill("OPEN");
        when(operations.readiness("BK-1")).thenReturn(new SettlementReadiness(true, true, true, Instant.now().minusSeconds(90_000)));
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());

        processor().process("BK-1");

        verify(loyalty).finishIneligibleEarn(argThat(outcome -> outcome.outcome() == OutcomeKind.WORKSHOP_NO_SHOW_FINAL
                && outcome.sourceRef().equals("BK-1") && outcome.snapshotRef().equals("QUOTE-1")
                && outcome.ownerModule().equals("workshopsettlement")));
        verify(loyalty, never()).grantEarnedPoints(any());
    }

    @Test
    void grantsOnlyWhenWindowIsClosedAndFinalizedBillHasNoOutstandingBalance() {
        SettlementFinalBill bill = readyBill("FINALIZED");
        bill.setRemainingAmountVnd(0L);
        when(operations.readiness("BK-1")).thenReturn(new SettlementReadiness(true, true, false, Instant.now().minusSeconds(90_000)));
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc("BILL-1", "CONFIRMED")).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());

        processor().process("BK-1");

        verify(loyalty).grantEarnedPoints(argThat(outcome -> outcome.outcome() == OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID
                && outcome.sourceOutcomeVersion() == 4L));
        verify(loyalty, never()).finishIneligibleEarn(any());
    }

    private SettlementFinalBill readyBill(String lifecycle) {
        SettlementFinalBill bill = SettlementFinalBill.builder().bookingReference("BK-1").billReference("BILL-1")
                .lifecycleStatus(lifecycle).remainingAmountVnd(0L).acceptedDepositAmountVnd(50L)
                .postPromotionAmountVnd(100L).currencyCode("VND").build();
        when(bills.findByBookingReferenceForUpdate("BK-1")).thenReturn(Optional.of(bill));
        return bill;
    }

    private void memberSource() {
        when(booking.loyaltyOutcomeSource("BK-1"))
                .thenReturn(new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingLoyaltyOutcomeSource(
                        true, "QUOTE-1", 4L, Instant.parse("2026-10-30T10:00:00Z")));
    }

    private static BenefitResult pendingEarn() {
        return new BenefitResult(SourceKind.WORKSHOP, "BK-1", ComponentState.CONSUMED,
                ComponentState.NONE, EarnState.PENDING, Instant.now());
    }

    private WorkshopLoyaltyOutcomeProcessor processor() {
        return new WorkshopLoyaltyOutcomeProcessor(bills, charges, evidence, booking, operations, loyalty);
    }
}
