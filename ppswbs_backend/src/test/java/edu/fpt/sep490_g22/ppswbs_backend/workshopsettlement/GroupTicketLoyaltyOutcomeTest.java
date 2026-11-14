package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingGroupLoyaltyOutcomeSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopCancellationLoyaltyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopLoyaltyOutcomeProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 003.3 FR-030 & BE-DESIGN-0002: Member Loyalty earn of a booking group.
 * The earn is decided per ticket with WORKSHOP_TICKET_* outcomes and partRef (L{line}-T{ordinal}).
 */
class GroupTicketLoyaltyOutcomeTest {

    private static final String GROUP = "GRP-TEST0001";
    private static final String TICKET_A = "GRP-TEST0001-L0-T1";
    private static final String TICKET_B = "GRP-TEST0001-L0-T2";
    private static final String OPS_A = "ops-ticket-a";
    private static final String OPS_B = "ops-ticket-b";

    private SettlementFinalBillRepository bills;
    private WorkshopBookingSettlementAdapter booking;
    private WorkshopOperationsSettlementAdapter operations;
    private LoyaltyFacade loyalty;
    private WorkshopLoyaltyOutcomeProcessor processor;
    private final List<SettlementFinalBill> folios = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bills = mock(SettlementFinalBillRepository.class);
        booking = mock(WorkshopBookingSettlementAdapter.class);
        operations = mock(WorkshopOperationsSettlementAdapter.class);
        loyalty = mock(LoyaltyFacade.class);
        SettlementChargeRepository charges = mock(SettlementChargeRepository.class);
        SettlementEvidenceConclusionRepository evidence = mock(SettlementEvidenceConclusionRepository.class);
        SettlementCaseRepository cases = mock(SettlementCaseRepository.class);
        WorkshopCancellationLoyaltyService cancellationLoyalty = mock(WorkshopCancellationLoyaltyService.class);
        when(bills.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bills.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc(anyString(), anyString()))
                .thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion(anyString(), anyString())).thenReturn(List.of());
        processor = new WorkshopLoyaltyOutcomeProcessor(bills, charges, evidence, cases, booking, operations,
                cancellationLoyalty, loyalty);

        folios.add(folio(TICKET_A));
        folios.add(folio(TICKET_B));
        when(bills.findByGroupReferenceOrderByIdAsc(GROUP)).thenReturn(folios);
        for (SettlementFinalBill folio : folios) {
            when(bills.findByBookingReferenceForUpdate(folio.getBookingReference())).thenReturn(Optional.of(folio));
        }
        when(booking.groupLoyaltyOutcomeSource(GROUP)).thenReturn(new BookingGroupLoyaltyOutcomeSource(true,
                "quote-1", 3L, Instant.parse("2026-11-12T10:12:00Z"),
                List.of(new BookingGroupLoyaltyOutcomeSource.GroupTicket(TICKET_A, OPS_A, "L0-T1"),
                        new BookingGroupLoyaltyOutcomeSource.GroupTicket(TICKET_B, OPS_B, "L0-T2"))));
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, GROUP)).thenReturn(result(EarnState.PENDING));
    }

    @Test
    void ticketAttendedAndSettledGrantsItsPoints() {
        givenAttendance(OPS_A, TicketAttendanceOutcome.Outcome.ATTENDED);

        processor.process(TICKET_A);

        ArgumentCaptor<AuthoritativeOutcome> granted = ArgumentCaptor.forClass(AuthoritativeOutcome.class);
        verify(loyalty).grantEarnedPoints(granted.capture());
        assertThat(granted.getValue().sourceRef()).isEqualTo(GROUP);
        assertThat(granted.getValue().outcome()).isEqualTo(OutcomeKind.WORKSHOP_TICKET_COMPLETED_AND_FULLY_PAID);
        assertThat(granted.getValue().sourceOutcomeKey()).isEqualTo("ticket-completed-" + TICKET_A);
        assertThat(granted.getValue().partRef()).isEqualTo("L0-T1");
        assertThat(granted.getValue().snapshotRef()).isEqualTo("quote-1");
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNotNull();
    }

    @Test
    void ticketAnUltimateNoShowClosesItsEarnInsteadOfGranting() {
        givenAttendance(OPS_A, TicketAttendanceOutcome.Outcome.NO_SHOW);

        processor.process(TICKET_A);

        ArgumentCaptor<AuthoritativeOutcome> closed = ArgumentCaptor.forClass(AuthoritativeOutcome.class);
        verify(loyalty).finishIneligibleEarn(closed.capture());
        assertThat(closed.getValue().sourceRef()).isEqualTo(GROUP);
        assertThat(closed.getValue().outcome()).isEqualTo(OutcomeKind.WORKSHOP_TICKET_NO_SHOW_FINAL);
        assertThat(closed.getValue().sourceOutcomeKey()).isEqualTo("ticket-no-show-" + TICKET_A);
        assertThat(closed.getValue().partRef()).isEqualTo("L0-T1");
        assertThat(closed.getValue().snapshotRef()).isEqualTo("quote-1");
        verify(loyalty, never()).grantEarnedPoints(any());
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNotNull();
    }

    @Test
    void ticketsInSameGroupAreDecidedIndependentlyPerTicket() {
        givenAttendance(OPS_A, TicketAttendanceOutcome.Outcome.NO_SHOW);
        givenAttendance(OPS_B, TicketAttendanceOutcome.Outcome.ATTENDED);

        processor.process(TICKET_A);
        processor.process(TICKET_B);

        verify(loyalty).finishIneligibleEarn(argThat(outcome ->
                outcome.outcome() == OutcomeKind.WORKSHOP_TICKET_NO_SHOW_FINAL
                        && "L0-T1".equals(outcome.partRef())));
        verify(loyalty).grantEarnedPoints(argThat(outcome ->
                outcome.outcome() == OutcomeKind.WORKSHOP_TICKET_COMPLETED_AND_FULLY_PAID
                        && "L0-T2".equals(outcome.partRef())));
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNotNull();
        assertThat(folios.get(1).getLoyaltyProcessedAt()).isNotNull();
    }

    @Test
    void aTicketStillInsideTheCorrectionWindowDecidesNothing() {
        when(operations.ticketAttendanceOutcome(OPS_A)).thenReturn(TicketAttendanceOutcome.pending());

        processor.process(TICKET_A);

        verifyNoMoreInteractions(ignoreStubs(loyalty));
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNull();
    }

    @Test
    void aTicketWithAnUnsettledFolioGrantsNothingYet() {
        givenAttendance(OPS_A, TicketAttendanceOutcome.Outcome.ATTENDED);
        folios.get(0).setLifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name());
        folios.get(0).setRemainingAmountVnd(300_000L);

        processor.process(TICKET_A);

        verify(loyalty, never()).grantEarnedPoints(any());
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNull();
    }

    @Test
    void theTicketFolioFindsTheWholeGroupEarnAlreadyDecided() {
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, GROUP)).thenReturn(result(EarnState.GRANTED));

        processor.process(TICKET_B);

        verify(loyalty, never()).grantEarnedPoints(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
        assertThat(folios.get(1).getLoyaltyProcessedAt()).isNotNull();
    }

    @Test
    void aTicketWhosePartIsAlreadyClosedFindsItsPartDecided() {
        BenefitPartRef partA = new BenefitPartRef("L0-T1", BigInteger.valueOf(300_000),
                BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
                BigInteger.valueOf(300_000), BigInteger.valueOf(30),
                ComponentState.NONE, EarnState.GRANTED, "COMPLETED");
        BenefitResult resultWithParts = new BenefitResult(SourceKind.WORKSHOP, GROUP, ComponentState.CONSUMED,
                ComponentState.CONSUMED, EarnState.PENDING, Instant.now(), List.of(partA));
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, GROUP)).thenReturn(resultWithParts);

        processor.process(TICKET_A);

        verify(loyalty, never()).grantEarnedPoints(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNotNull();
    }

    @Test
    void aGuestGroupHasNoEarnToDecide() {
        when(booking.groupLoyaltyOutcomeSource(GROUP)).thenReturn(BookingGroupLoyaltyOutcomeSource.none());

        processor.process(TICKET_A);

        verify(loyalty, never()).findBenefitResult(any(), anyString());
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNotNull();
    }

    @Test
    void aTicketWithoutItsOperationsIdentityDecidesNothing() {
        when(booking.groupLoyaltyOutcomeSource(GROUP)).thenReturn(new BookingGroupLoyaltyOutcomeSource(true,
                "quote-1", 3L, Instant.parse("2026-11-12T10:12:00Z"),
                List.of(new BookingGroupLoyaltyOutcomeSource.GroupTicket(TICKET_A, null, "L0-T1"))));

        processor.process(TICKET_A);

        verifyNoInteractions(operations);
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNull();
    }

    // ---- helpers ----

    private void givenAttendance(String operationsTicketReference, TicketAttendanceOutcome.Outcome outcome) {
        when(operations.ticketAttendanceOutcome(operationsTicketReference))
                .thenReturn(new TicketAttendanceOutcome(true, outcome, null));
    }

    private static SettlementFinalBill folio(String ticketCode) {
        return SettlementFinalBill.builder()
                .billReference("SETTLE-BILL-" + ticketCode)
                .bookingReference(ticketCode)
                .groupReference(GROUP)
                .postPromotionAmountVnd(300_000L)
                .acceptedDepositAmountVnd(300_000L)
                .remainingAmountVnd(0L)
                .currencyCode("VND")
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.FINALIZED.name())
                .build();
    }

    private static BenefitResult result(EarnState earnState) {
        return new BenefitResult(SourceKind.WORKSHOP, GROUP, ComponentState.CONSUMED, ComponentState.CONSUMED,
                earnState, Instant.parse("2026-11-12T10:00:00Z"));
    }
}
