package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConsumeBuyerProofResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReplaceTicketSessionsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketSessionReplacementOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GroupTicketChangeItemDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GroupTicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GroupTicketChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 003.3 T118 (FR-044, FR-045): several tickets of one group change together, all or none. */
class GroupTicketChangeServiceTest {

    private static final String GROUP = "GRP-01";
    private static final String KEY = "idem-key-group-change-01";
    private static final String PROOF = "bvr-" + "a".repeat(20);

    private final WorkshopBookingFacade booking = mock(WorkshopBookingFacade.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final SettlementFinalBillRepository finalBills = mock(SettlementFinalBillRepository.class);
    private final SettlementTicketChangeRepository changeRepository = mock(SettlementTicketChangeRepository.class);
    private final FolioBalanceService folioBalanceService = mock(FolioBalanceService.class);
    private final SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
    private final LoyaltyFacade loyalty = mock(LoyaltyFacade.class);
    private GroupTicketChangeService service;

    private final UUID sessionA = UUID.randomUUID();
    private final UUID sessionB = UUID.randomUUID();
    private final UUID opsTicket1 = UUID.randomUUID();
    private final UUID opsTicket2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TicketChangeService changes = new TicketChangeService(booking, operations, finalBills, changeRepository,
                folioBalanceService, audits, loyalty,
                new WorkshopSettlementAuthorizationService(mock(AccountFacade.class), audits, operations),
                mock(SettlementTicketChangeRequestRepository.class));
        service = new GroupTicketChangeService(booking, operations, finalBills, changeRepository, folioBalanceService,
                changes);
        when(folioBalanceService.balanceOf(any())).thenReturn(new FolioBalanceService.FolioBalance(100_000L, 0L, 100_000L));
        when(booking.consumeBuyerProof(any())).thenReturn(ConsumeBuyerProofResult.accepted());
    }

    private TicketFolioContext ticket(String code, UUID opsRef, long voucher, String group) {
        return new TicketFolioContext(code, group, "L1", "7", 10L, List.of(UUID.randomUUID().toString()), null,
                opsRef.toString(), Instant.now().plus(72, ChronoUnit.HOURS), 0L, "L1-" + code, voucher);
    }

    private SettlementFinalBill folio(String code) {
        return SettlementFinalBill.builder().billReference("BILL-" + code).bookingReference(code).groupReference(GROUP)
                .postPromotionAmountVnd(200_000L).acceptedDepositAmountVnd(100_000L).remainingAmountVnd(100_000L)
                .currencyCode("VND").lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name()).build();
    }

    private void givenTwoOpenTickets(long voucher1, long voucher2) {
        when(booking.getTicketFolioContext("T1")).thenReturn(Optional.of(ticket("T1", opsTicket1, voucher1, GROUP)));
        when(booking.getTicketFolioContext("T2")).thenReturn(Optional.of(ticket("T2", opsTicket2, voucher2, GROUP)));
        when(finalBills.findByBookingReference("T1")).thenReturn(Optional.of(folio("T1")));
        when(finalBills.findByBookingReference("T2")).thenReturn(Optional.of(folio("T2")));
    }

    private GroupTicketChangeRequestDto request(Boolean confirm) {
        return new GroupTicketChangeRequestDto(TicketChangeType.ADVANCE, List.of(
                new GroupTicketChangeItemDto("T1", List.of(sessionA)),
                new GroupTicketChangeItemDto("T2", List.of(sessionB))), confirm, PROOF, null);
    }

    private void outOfScope(long lost) {
        for (UUID session : List.of(sessionA, sessionB)) {
            when(operations.saleEligibility(session)).thenReturn(new SaleEligibilitySnapshot(session, true, "ACTIVE", 20L,
                    Instant.now().plus(96, ChronoUnit.HOURS), Instant.now().plus(98, ChronoUnit.HOURS), 5,
                    LocalDate.now().plusDays(4)));
        }
        when(loyalty.checkTicketVoucherScope(any())).thenReturn(new TicketVoucherScope(true, false, BigInteger.valueOf(lost)));
    }

    @Test
    @DisplayName("two tickets: one 003.2 call carries both lines, each spends its own allowance, the proof is spent once")
    void changesBothTicketsInOneCall() {
        givenTwoOpenTickets(0L, 0L);
        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());

        GroupTicketChangeResultDto result = service.changeGroupTickets(GROUP, request(null), KEY, "CORR", null);

        assertThat(result.applied()).isTrue();
        assertThat(result.tickets()).hasSize(2);
        ArgumentCaptor<ReplaceTicketSessionsCommand> command = ArgumentCaptor.forClass(ReplaceTicketSessionsCommand.class);
        verify(operations, times(1)).replaceTicketSessions(command.capture());
        assertThat(command.getValue().lines()).hasSize(2);
        assertThat(command.getValue().type()).isEqualTo(TicketReplacementType.CUSTOMER_ADVANCE);
        verify(changeRepository, times(2)).save(org.mockito.ArgumentMatchers.argThat(
                (SettlementTicketChange c) -> c.isCounted() && c.isApplied() && KEY.equals(c.getIdempotencyKey())));
        verify(booking, times(1)).consumeBuyerProof(any());
    }

    @Test
    @DisplayName("all or none: one ticket without allowance refuses the whole change, 003.2 is not called")
    void oneTicketWithoutAllowance_refusesAll() {
        givenTwoOpenTickets(0L, 0L);
        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue("T2")).thenReturn(1L);

        assertThatThrownBy(() -> service.changeGroupTickets(GROUP, request(null), KEY, "CORR", null))
                .isInstanceOfSatisfying(GroupCancellationConflictException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("CHANGE_ALLOWANCE_EXHAUSTED");
                    assertThat(error.getFailingTicketCodes()).containsExactly("T2");
                });
        verify(operations, never()).replaceTicketSessions(any());
        verify(changeRepository, never()).save(any());
        verify(booking, never()).consumeBuyerProof(any());
    }

    @Test
    @DisplayName("all or none: 003.2 refuses one line (no seat) -> nothing is recorded and the proof is not spent")
    void operationsRefusesOneLine_nothingRecorded() {
        givenTwoOpenTickets(0L, 0L);
        when(operations.replaceTicketSessions(any())).thenReturn(
                TicketSessionReplacementOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY, List.of(1)));

        assertThatThrownBy(() -> service.changeGroupTickets(GROUP, request(null), KEY, "CORR", null))
                .isInstanceOfSatisfying(GroupCancellationConflictException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("INSUFFICIENT_CAPACITY");
                    assertThat(error.getFailingTicketCodes()).containsExactly("T2");
                });
        verify(changeRepository, never()).save(any());
        verify(booking, never()).consumeBuyerProof(any());
    }

    @Test
    @DisplayName("voucher-scope losses are summed; the buyer confirms the total once before anything moves")
    void priceIncreasesAreSummedAndConfirmedOnce() {
        givenTwoOpenTickets(30_000L, 40_000L);
        outOfScope(30_000L);

        GroupTicketChangeResultDto preview = service.changeGroupTickets(GROUP, request(null), KEY, "CORR", null);

        assertThat(preview.applied()).isFalse();
        assertThat(preview.confirmIncreaseRequired()).isTrue();
        assertThat(preview.totalPriceIncrease()).isEqualTo("60000");
        verify(operations, never()).replaceTicketSessions(any());

        when(operations.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        GroupTicketChangeResultDto applied = service.changeGroupTickets(GROUP, request(true), KEY, "CORR", null);

        assertThat(applied.applied()).isTrue();
        assertThat(applied.totalPriceIncrease()).isEqualTo("60000");
        verify(finalBills, times(2)).save(org.mockito.ArgumentMatchers.argThat(
                (SettlementFinalBill bill) -> bill.getRemainingAmountVnd() == 130_000L));
    }

    @Test
    @DisplayName("a ticket of another group, or a repeated ticket, is refused")
    void foreignOrDuplicateTicketRefused() {
        when(booking.getTicketFolioContext("T1")).thenReturn(Optional.of(ticket("T1", opsTicket1, 0L, "OTHER-GROUP")));

        assertThatThrownBy(() -> service.changeGroupTickets(GROUP, new GroupTicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(new GroupTicketChangeItemDto("T1", List.of(sessionA))), null, PROOF, null),
                KEY, "CORR", null)).isInstanceOf(WorkshopSettlementNotFoundException.class);
        assertThatThrownBy(() -> service.changeGroupTickets(GROUP, new GroupTicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(new GroupTicketChangeItemDto("T1", List.of(sessionA)),
                new GroupTicketChangeItemDto("T1", List.of(sessionB))), null, PROOF, null), KEY, "CORR", null))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessageContaining("DUPLICATE_TICKET");
    }

    @Test
    @DisplayName("replay with the same Idempotency-Key returns the stored result and does not call 003.2 again")
    void replayReturnsStoredResult() {
        for (String code : List.of("T1", "T2")) {
            when(changeRepository.findByTicketCodeAndIdempotencyKey(code, KEY)).thenReturn(Optional.of(
                    SettlementTicketChange.builder().ticketCode(code).applied(true).counted(true)
                            .toSessionReferences(sessionA.toString()).priceIncreaseAmountVnd(0L)
                            .balanceDueSnapshot(100_000L).build()));
        }

        GroupTicketChangeResultDto result = service.changeGroupTickets(GROUP, request(null), KEY, "CORR", null);

        assertThat(result.applied()).isTrue();
        assertThat(result.tickets()).hasSize(2);
        verify(operations, never()).replaceTicketSessions(any());
    }
}
