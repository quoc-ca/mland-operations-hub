package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome.Outcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopCancellationLoyaltyService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkshopLoyaltyOutcomeProcessorTest {
    @Mock SettlementFinalBillRepository bills;
    @Mock SettlementChargeRepository charges;
    @Mock SettlementEvidenceConclusionRepository evidence;
    @Mock SettlementCaseRepository cases;
    @Mock WorkshopCancellationLoyaltyService cancellationLoyalty;
    @Mock WorkshopBookingSettlementAdapter booking;
    @Mock WorkshopOperationsSettlementAdapter operations;
    @Mock LoyaltyFacade loyalty;

    @Test
    void waitsForAttendanceOutcomeTerminalBeforeEmittingOutcome() {
        readyBill("OPEN");
        when(booking.loyaltyOutcomeSource("BK-1"))
                .thenReturn(new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingLoyaltyOutcomeSource(
                        true, "QUOTE-1", 4L, Instant.parse("2026-10-30T10:00:00Z")));
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(false, null));

        processor().process("BK-1");

        verify(loyalty, never()).grantEarnedPoints(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
    }

    @Test
    void finishesEarnForNoShowWhenFolioIsClosed() {
        readyBill("CLOSED");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.NO_SHOW));

        processor().process("BK-1");

        verify(loyalty).finishIneligibleEarn(argThat(outcome -> outcome.outcome() == OutcomeKind.WORKSHOP_NO_SHOW_FINAL
                && outcome.sourceRef().equals("BK-1") && outcome.snapshotRef().equals("QUOTE-1")
                && outcome.ownerModule().equals("workshopsettlement")));
        verify(loyalty, never()).grantEarnedPoints(any());
    }

    @Test
    void finishesEarnForNoShowWhenFolioIsOpen() {
        readyBill("OPEN");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.NO_SHOW));

        processor().process("BK-1");

        verify(loyalty).finishIneligibleEarn(argThat(outcome -> outcome.outcome() == OutcomeKind.WORKSHOP_NO_SHOW_FINAL
                && outcome.sourceRef().equals("BK-1") && outcome.snapshotRef().equals("QUOTE-1")
                && outcome.ownerModule().equals("workshopsettlement")));
        verify(loyalty, never()).grantEarnedPoints(any());
    }

    @Test
    void grantsEarnedPointsForAttendedWhenFinalizedWithZeroBalance() {
        SettlementFinalBill bill = readyBill("FINALIZED");
        bill.setRemainingAmountVnd(0L);
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.ATTENDED));
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc("BILL-1", "CONFIRMED")).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());

        processor().process("BK-1");

        verify(loyalty).grantEarnedPoints(argThat(outcome -> outcome.outcome() == OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID
                && outcome.sourceOutcomeVersion() == 4L));
        verify(loyalty, never()).finishIneligibleEarn(any());
    }

    @Test
    void grantsEarnedPointsForPartialAttendanceWhenFinalizedWithZeroBalance_DecisionD6() {
        // D6: a PARTIAL booking earns Loyalty points like ATTENDED once its folio is FINALIZED with zero balance
        SettlementFinalBill bill = readyBill("FINALIZED");
        bill.setRemainingAmountVnd(0L);
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.PARTIAL));
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc("BILL-1", "CONFIRMED")).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());

        processor().process("BK-1");

        verify(loyalty).grantEarnedPoints(argThat(outcome -> outcome.outcome() == OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID
                && outcome.sourceOutcomeVersion() == 4L));
        verify(loyalty, never()).finishIneligibleEarn(any());
    }

    @Test
    void neverGrantsPointsWhenFolioIsClosedEvenIfAttended_Invariant() {
        // Invariant: a CLOSED folio never grants points
        readyBill("CLOSED");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.ATTENDED));

        processor().process("BK-1");

        verify(loyalty, never()).grantEarnedPoints(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
    }

    @Test
    void neverGrantsPointsWhenFolioIsClosedEvenIfPartial_Invariant() {
        // Invariant: a CLOSED folio never grants points
        readyBill("CLOSED");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.PARTIAL));

        processor().process("BK-1");

        verify(loyalty, never()).grantEarnedPoints(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
    }

    @Test
    void doesNotGrantPointsWhenBalanceIsOutstanding() {
        SettlementFinalBill bill = readyBill("FINALIZED");
        bill.setRemainingAmountVnd(10_000L);
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.ATTENDED));
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc("BILL-1", "CONFIRMED")).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());

        processor().process("BK-1");

        verify(loyalty, never()).grantEarnedPoints(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
    }

    @Test
    void doesNotGrantPointsWhenFolioIsOpenAndAttended() {
        readyBill("OPEN");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.ATTENDED));

        processor().process("BK-1");

        verify(loyalty, never()).grantEarnedPoints(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
    }

    @Test
    void ignoresGuestBookings() {
        readyBill("FINALIZED");
        when(booking.loyaltyOutcomeSource("BK-1"))
                .thenReturn(new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingLoyaltyOutcomeSource(
                        false, null, 1L, Instant.now()));

        processor().process("BK-1");

        verifyNoInteractions(loyalty);
    }

    @Test
    void idempotentWhenEarnIsNotPending() {
        readyBill("FINALIZED");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(new BenefitResult(
                SourceKind.WORKSHOP, "BK-1", ComponentState.CONSUMED, ComponentState.NONE,
                EarnState.GRANTED, Instant.now()));

        processor().process("BK-1");

        verify(loyalty, never()).grantEarnedPoints(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
    }

    @Test
    void neverFallsBackToTheOldAnyNoShowRuleWhenAttendanceIsNotTerminal() {
        // D6 replaced "any ticket no-show -> no earn": only the authoritative attendance outcome decides.
        SettlementFinalBill bill = readyBill("FINALIZED");
        bill.setRemainingAmountVnd(0L);
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(null);

        processor().process("BK-1");

        verify(operations, never()).readiness(any());
        verify(loyalty, never()).grantEarnedPoints(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
        assertThat(bill.getLoyaltyProcessedAt()).isNull();
    }

    @Test
    void partialAttendanceIsNeverTreatedAsNoShow() {
        SettlementFinalBill bill = readyBill("OPEN");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.PARTIAL));

        processor().process("BK-1");

        verify(loyalty, never()).finishIneligibleEarn(any());
        assertThat(bill.getLoyaltyProcessedAt()).isNull();
    }

    @Test
    void closedFolioFromAcceptedCustomerCancellationRestoresValidCancellationOnce() {
        SettlementFinalBill bill = readyBill("CLOSED");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(false, null));
        SettlementCase cancellation = SettlementCase.builder().caseReference("CASE-C").bookingReference("BK-1")
                .caseType("CUSTOMER_CANCELLATION").lifecycleStatus("RESOLVED").build();
        when(cases.findByBookingReferenceAndCaseTypeInAndLifecycleStatusOrderByCreatedAtAsc(
                "BK-1", List.of("CUSTOMER_CANCELLATION", "MLAND_CANCELLATION_REMEDY"), "RESOLVED"))
                .thenReturn(List.of(cancellation));

        processor().process("BK-1");

        verify(cancellationLoyalty).restoreVerifiedCancellation(cancellation, "case:CASE-C");
        verify(loyalty, never()).grantEarnedPoints(any());
        assertThat(bill.getLoyaltyProcessedAt()).isNotNull();
    }

    @Test
    void closedFolioFromMlandRemedyRestoresValidCancellationForAMember() {
        SettlementFinalBill bill = readyBill("CLOSED");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(false, null));
        SettlementCase remedy = SettlementCase.builder().caseReference("CASE-R").bookingReference("BK-1")
                .caseType("MLAND_CANCELLATION_REMEDY").lifecycleStatus("RESOLVED").build();
        when(cases.findByBookingReferenceAndCaseTypeInAndLifecycleStatusOrderByCreatedAtAsc(
                "BK-1", List.of("CUSTOMER_CANCELLATION", "MLAND_CANCELLATION_REMEDY"), "RESOLVED"))
                .thenReturn(List.of(remedy));

        processor().process("BK-1");

        verify(cancellationLoyalty).restoreVerifiedCancellation(remedy, "case:CASE-R");
        assertThat(bill.getLoyaltyProcessedAt()).isNotNull();
    }

    @Test
    void closedFolioWithoutAResolvedCancellationStaysUndecided() {
        SettlementFinalBill bill = readyBill("CLOSED");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(false, null));

        processor().process("BK-1");

        verifyNoInteractions(cancellationLoyalty);
        verify(loyalty, never()).grantEarnedPoints(any());
        assertThat(bill.getLoyaltyProcessedAt()).isNull();
    }

    @Test
    void decidedOutcomesMarkTheFolioProcessedSoTheSchedulerStopsScanningIt() {
        SettlementFinalBill noShow = readyBill("CLOSED");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.NO_SHOW));
        processor().process("BK-1");
        assertThat(noShow.getLoyaltyProcessedAt()).isNotNull();

        // A processed folio is skipped entirely, even if the run is repeated.
        clearInvocations(loyalty, booking, operations);
        processor().process("BK-1");
        verifyNoInteractions(loyalty, booking, operations);
    }

    @Test
    void guestFolioIsMarkedProcessedBecauseItHasNoLoyaltyOutcome() {
        SettlementFinalBill bill = readyBill("FINALIZED");
        when(booking.loyaltyOutcomeSource("BK-1"))
                .thenReturn(new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingLoyaltyOutcomeSource(
                        false, null, 1L, Instant.now()));

        processor().process("BK-1");

        verifyNoInteractions(loyalty);
        assertThat(bill.getLoyaltyProcessedAt()).isNotNull();
    }

    @Test
    void aLoyaltyFailureIsNotSwallowedSoTheRunRollsBackAndTheSchedulerRetries() {
        SettlementFinalBill bill = readyBill("CLOSED");
        memberSource();
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1")).thenReturn(pendingEarn());
        when(operations.attendanceOutcome("BK-1")).thenReturn(new BookingAttendanceOutcome(true, Outcome.NO_SHOW));
        doThrow(new IllegalStateException("loyalty down")).when(loyalty).finishIneligibleEarn(any());

        assertThatThrownBy(() -> processor().process("BK-1")).isInstanceOf(IllegalStateException.class);
        assertThat(bill.getLoyaltyProcessedAt()).isNull();
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
        return new WorkshopLoyaltyOutcomeProcessor(bills, charges, evidence, cases, booking, operations, cancellationLoyalty, loyalty);
    }
}