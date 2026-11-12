package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingGroupLoyaltyOutcomeSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 003.3 FR-030: the Loyalty earn of a booking group. The group holds one accepted transaction, so the earn
 * is one decision for the whole group, taken from each ticket's own attendance.
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
                List.of(new BookingGroupLoyaltyOutcomeSource.GroupTicket(TICKET_A, OPS_A),
                        new BookingGroupLoyaltyOutcomeSource.GroupTicket(TICKET_B, OPS_B))));
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, GROUP)).thenReturn(result(EarnState.PENDING));
    }

    @Test
    void theWholeGroupAttendedAndSettledGrantsItsPointsOnce() {
        givenAttendance(OPS_A, BookingAttendanceOutcome.Outcome.ATTENDED);
        givenAttendance(OPS_B, BookingAttendanceOutcome.Outcome.PARTIAL);

        processor.process(TICKET_A);

        ArgumentCaptor<AuthoritativeOutcome> granted = ArgumentCaptor.forClass(AuthoritativeOutcome.class);
        verify(loyalty).grantEarnedPoints(granted.capture());
        assertThat(granted.getValue().sourceRef()).isEqualTo(GROUP);
        assertThat(granted.getValue().outcome()).isEqualTo(OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID);
        assertThat(granted.getValue().sourceOutcomeKey()).isEqualTo("group-completed-" + GROUP);
        assertThat(granted.getValue().snapshotRef()).isEqualTo("quote-1");
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNotNull();
    }

    @Test
    void everyTicketAnUltimateNoShowClosesTheEarnInsteadOfGranting() {
        givenAttendance(OPS_A, BookingAttendanceOutcome.Outcome.NO_SHOW);
        givenAttendance(OPS_B, BookingAttendanceOutcome.Outcome.NO_SHOW);

        processor.process(TICKET_A);

        ArgumentCaptor<AuthoritativeOutcome> closed = ArgumentCaptor.forClass(AuthoritativeOutcome.class);
        verify(loyalty).finishIneligibleEarn(closed.capture());
        assertThat(closed.getValue().outcome()).isEqualTo(OutcomeKind.WORKSHOP_NO_SHOW_FINAL);
        assertThat(closed.getValue().sourceOutcomeKey()).isEqualTo("group-no-show-" + GROUP);
        verify(loyalty, never()).grantEarnedPoints(any());
    }

    @Test
    void oneTicketThatShowedUpKeepsTheGroupsEarn() {
        givenAttendance(OPS_A, BookingAttendanceOutcome.Outcome.NO_SHOW);
        givenAttendance(OPS_B, BookingAttendanceOutcome.Outcome.ATTENDED);

        processor.process(TICKET_A);

        verify(loyalty, never()).finishIneligibleEarn(any());
        verify(loyalty).grantEarnedPoints(any());
    }

    @Test
    void aTicketStillInsideTheCorrectionWindowDecidesNothing() {
        givenAttendance(OPS_A, BookingAttendanceOutcome.Outcome.ATTENDED);
        when(operations.ticketAttendanceOutcome(OPS_B)).thenReturn(BookingAttendanceOutcome.pending());

        processor.process(TICKET_A);

        verifyNoMoreInteractions(ignoreStubs(loyalty));
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNull();
    }

    @Test
    void aGroupWithAnUnsettledFolioGrantsNothingYet() {
        givenAttendance(OPS_A, BookingAttendanceOutcome.Outcome.ATTENDED);
        givenAttendance(OPS_B, BookingAttendanceOutcome.Outcome.ATTENDED);
        folios.get(1).setLifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name());
        folios.get(1).setRemainingAmountVnd(300_000L);

        processor.process(TICKET_A);

        verify(loyalty, never()).grantEarnedPoints(any());
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNull();
    }

    @Test
    void theSecondTicketFolioFindsTheEarnAlreadyDecided() {
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, GROUP)).thenReturn(result(EarnState.GRANTED));

        processor.process(TICKET_B);

        verify(loyalty, never()).grantEarnedPoints(any());
        verify(loyalty, never()).finishIneligibleEarn(any());
        assertThat(folios.get(1).getLoyaltyProcessedAt()).isNotNull();
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
                List.of(new BookingGroupLoyaltyOutcomeSource.GroupTicket(TICKET_A, null))));

        processor.process(TICKET_A);

        verifyNoInteractions(operations);
        assertThat(folios.get(0).getLoyaltyProcessedAt()).isNull();
    }

    // ---- helpers ----

    private void givenAttendance(String operationsTicketReference, BookingAttendanceOutcome.Outcome outcome) {
        when(operations.ticketAttendanceOutcome(operationsTicketReference))
                .thenReturn(new BookingAttendanceOutcome(true, outcome));
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
