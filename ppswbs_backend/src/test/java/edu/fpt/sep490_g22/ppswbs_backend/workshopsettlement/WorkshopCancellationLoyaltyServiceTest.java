package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingLoyaltyOutcomeSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopCancellationLoyaltyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkshopCancellationLoyaltyServiceTest {

    @Mock
    private WorkshopBookingSettlementAdapter booking;

    @Mock
    private LoyaltyFacade loyalty;

    @InjectMocks
    private WorkshopCancellationLoyaltyService service;

    @Test
    void customerCancellationRestoresBenefitsAndClosesEarnInOrder() {
        SettlementCase settlementCase = SettlementCase.builder()
                .caseReference("CASE-CC-1")
                .bookingReference("BK-1")
                .caseType("CUSTOMER_CANCELLATION")
                .build();

        when(booking.loyaltyOutcomeSource("BK-1"))
                .thenReturn(new WorkshopBookingLoyaltyOutcomeSource(true, "QUOTE-1", 3L, Instant.now()));
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-1"))
                .thenReturn(new BenefitResult(SourceKind.WORKSHOP, "BK-1", ComponentState.CONSUMED,
                        ComponentState.NONE, EarnState.PENDING, Instant.now()));

        service.restoreVerifiedCancellation(settlementCase, "proof-evidence-1");

        InOrder inOrder = inOrder(loyalty);
        inOrder.verify(loyalty).restoreCancelledWorkshopBenefits(argThat(outcome ->
                outcome.outcome() == OutcomeKind.VALID_WORKSHOP_CANCELLATION
                        && outcome.sourceOutcomeKey().equals("valid-cancellation-CASE-CC-1")
                        && outcome.sourceOutcomeVersion() == 3L
                        && outcome.snapshotRef().equals("QUOTE-1")
                        && outcome.proofRef().equals("proof-evidence-1")));

        inOrder.verify(loyalty).finishIneligibleEarn(argThat(outcome ->
                outcome.outcome() == OutcomeKind.VALID_WORKSHOP_CANCELLATION
                        && outcome.sourceOutcomeKey().equals("valid-cancellation-CASE-CC-1-earn")
                        && outcome.sourceOutcomeVersion() == 4L
                        && outcome.snapshotRef().equals("QUOTE-1")
                        && outcome.proofRef().equals("proof-evidence-1")));
    }

    @Test
    void mlandCancellationRemedyRestoresBenefitsAndClosesEarn_DecisionD15() {
        // D15: A Mland-caused remedy of a Member booking emits Loyalty VALID_WORKSHOP_CANCELLATION
        SettlementCase settlementCase = SettlementCase.builder()
                .caseReference("CASE-MLAND-1")
                .bookingReference("BK-2")
                .caseType("MLAND_CANCELLATION_REMEDY")
                .build();

        when(booking.loyaltyOutcomeSource("BK-2"))
                .thenReturn(new WorkshopBookingLoyaltyOutcomeSource(true, "QUOTE-2", 5L, Instant.now()));
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-2"))
                .thenReturn(new BenefitResult(SourceKind.WORKSHOP, "BK-2", ComponentState.CONSUMED,
                        ComponentState.NONE, EarnState.PENDING, Instant.now()));

        service.restoreVerifiedCancellation(settlementCase, "handoff-ref-1");

        InOrder inOrder = inOrder(loyalty);
        inOrder.verify(loyalty).restoreCancelledWorkshopBenefits(argThat(outcome ->
                outcome.outcome() == OutcomeKind.VALID_WORKSHOP_CANCELLATION
                        && outcome.sourceOutcomeKey().equals("valid-cancellation-CASE-MLAND-1")
                        && outcome.sourceOutcomeVersion() == 5L));

        inOrder.verify(loyalty).finishIneligibleEarn(argThat(outcome ->
                outcome.outcome() == OutcomeKind.VALID_WORKSHOP_CANCELLATION
                        && outcome.sourceOutcomeKey().equals("valid-cancellation-CASE-MLAND-1-earn")
                        && outcome.sourceOutcomeVersion() == 6L));
    }

    @Test
    void ignoresUnsupportedCaseTypes() {
        SettlementCase settlementCase = SettlementCase.builder()
                .caseReference("CASE-OTHER-1")
                .bookingReference("BK-3")
                .caseType("OTHER_CASE_TYPE")
                .build();

        service.restoreVerifiedCancellation(settlementCase, "proof");

        verifyNoInteractions(booking, loyalty);
    }

    @Test
    void ignoresGuestBookings() {
        SettlementCase settlementCase = SettlementCase.builder()
                .caseReference("CASE-GUEST-1")
                .bookingReference("BK-4")
                .caseType("CUSTOMER_CANCELLATION")
                .build();

        when(booking.loyaltyOutcomeSource("BK-4"))
                .thenReturn(new WorkshopBookingLoyaltyOutcomeSource(false, null, 1L, Instant.now()));

        service.restoreVerifiedCancellation(settlementCase, "proof");

        verifyNoInteractions(loyalty);
    }

    @Test
    void replayIgnoredWhenEarnIsNotPending() {
        SettlementCase settlementCase = SettlementCase.builder()
                .caseReference("CASE-REPLAY-1")
                .bookingReference("BK-5")
                .caseType("CUSTOMER_CANCELLATION")
                .build();

        when(booking.loyaltyOutcomeSource("BK-5"))
                .thenReturn(new WorkshopBookingLoyaltyOutcomeSource(true, "QUOTE-5", 2L, Instant.now()));
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, "BK-5"))
                .thenReturn(new BenefitResult(SourceKind.WORKSHOP, "BK-5", ComponentState.RESTORED,
                        ComponentState.NONE, EarnState.INELIGIBLE, Instant.now()));

        service.restoreVerifiedCancellation(settlementCase, "proof");

        verify(loyalty, never()).restoreCancelledWorkshopBenefits(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
    }
}

