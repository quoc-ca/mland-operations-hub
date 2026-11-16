package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashDayCloseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashReversalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.CashCloseRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketFolioDtos.CashReversalDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * 003.3 T144 and T147 (FR-036, FR-053): the Manager-only cash reversal of a closed day, on the Flyway-built MySQL
 * schema. The closed day and the original collection are never edited; the folio balance and the next day's expected
 * cash move.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {
        "vnpay.merchant-api-ip-address=127.0.0.1",
        "vnpay.merchant-api-created-by=it-merchant",
        "workshop.operations.visible-location-ids="})
class CashReversalMysqlIT {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(300L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());

    @MockitoBean PaymentGatewayCheckoutPort checkout;
    @MockitoBean VnpayMerchantOperationsPort merchantOperations;
    @MockitoBean WorkshopOperationsFacade operationsFacade;
    @MockitoBean LoyaltyFacade loyalty;
    @MockitoBean WorkshopBookingFacade bookingFacade;
    @MockitoBean edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade bookingNotifications;

    @Autowired SettlementFinalBillRepository bills;
    @Autowired SettlementFolioCollectionRepository collections;
    @Autowired SettlementCashDayCloseRepository closes;
    @Autowired SettlementCashReversalRepository reversals;
    @Autowired FolioCollectionService collectionService;
    @Autowired FolioCollectionApiService collectionApi;
    @Autowired CashReversalService reversalService;
    @Autowired CashDayCloseService cashClose;
    @Autowired FolioBalanceService balances;
    @Autowired TicketFolioApiService folioApi;

    private static String unique(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }

    private static String key() {
        return "it-idempotency-key-" + UUID.randomUUID();
    }

    private SettlementFinalBill folio(String ticket, long remaining, String status) {
        return bills.save(SettlementFinalBill.builder().billReference("BILL-" + ticket).bookingReference(ticket)
                .groupReference("G-" + ticket).postPromotionAmountVnd(remaining).acceptedDepositAmountVnd(0L)
                .remainingAmountVnd(remaining).currencyCode("VND").lifecycleStatus(status).build());
    }

    private void ctx(String ticket) {
        doReturn(FinalBalanceResolutionOutcome.accepted()).when(bookingFacade).recordTicketFinalBalanceResolved(any());
        doReturn(Optional.of(new TicketFolioContext(ticket, "G-" + ticket, "G-" + ticket + "-L1", "7", 10L, List.of(),
                null, null, Instant.now().plus(72, ChronoUnit.HOURS), 0L, "L1-" + ticket, 0L)))
                .when(bookingFacade).getTicketFolioContext(ticket);
    }

    private static long newBranch() {
        return 9_300_000L + SEQ.incrementAndGet();
    }

    private String cashIn(String ticket, long amount, long branch) {
        return collectionService.collectCash(ticket, amount, amount, "RCPT-" + unique("R"), "payer", branch, 300L,
                "CORR").collectionReference();
    }

    /** Submits the counted amount: an exact count closes the day, a different one waits for the Manager. */
    private void closeDay(long branch, long counted, String reason) {
        collectionApi.closeCashDay(new CashCloseRequest(branch, TODAY, String.valueOf(counted), reason), key(), "CORR",
                MANAGER);
    }

    private long balanceDue(String ticket) {
        return balances.balanceOf(bills.findByBookingReference(ticket).orElseThrow()).balanceDue();
    }

    @Test
    @DisplayName("a cash reversal after the close: balance recomputed, next day's expected cash falls, closed day untouched")
    void reversalAfterCloseRecomputesBalanceAndNextDay() {
        String ticket = unique("T-REV-");
        folio(ticket, 200_000, "OPEN");
        ctx(ticket);
        long branch = newBranch();
        String collection = cashIn(ticket, 120_000, branch);
        closeDay(branch, 120_000, null);
        var closeBefore = closes.findByBranchIdAndBusinessDay(branch, TODAY).orElseThrow();
        assertThat(balanceDue(ticket)).isEqualTo(80_000);

        CashReversalDto reversal = reversalService.reverse(ticket, collection, key(), "CORR", "Counted twice",
                MANAGER);

        assertThat(reversal.amountVnd()).isEqualTo("-120000");
        assertThat(reversal.collectionId()).isEqualTo(collection);
        assertThat(reversal.originalWorkingDate()).isEqualTo(TODAY);
        // the counted amount of today was submitted, so the reversal belongs to the next day (FR-036 cut-off)
        assertThat(reversal.workingDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(balanceDue(ticket)).isEqualTo(200_000);
        assertThat(cashClose.expectedCash(branch, TODAY.plusDays(1))).isEqualTo(-120_000);
        // the closed day and the original collection are never edited
        var closeAfter = closes.findByBranchIdAndBusinessDay(branch, TODAY).orElseThrow();
        assertThat(closeAfter.getExpectedCashVnd()).isEqualTo(closeBefore.getExpectedCashVnd());
        assertThat(closeAfter.getCountedCashVnd()).isEqualTo(closeBefore.getCountedCashVnd());
        assertThat(closeAfter.getStatus()).isEqualTo(closeBefore.getStatus());
        assertThat(cashClose.expectedCash(branch, TODAY)).isEqualTo(120_000);
        var original = collections.findByCollectionReference(collection).orElseThrow();
        assertThat(original.getStatus()).isEqualTo(FolioCollectionStatus.CONFIRMED);
        assertThat(original.getAmountVnd()).isEqualTo(120_000);
    }

    @Test
    @DisplayName("T147: cash recorded between submission and the Manager's confirmation gets the next day; the count stays")
    void cutOffIsTheSubmissionOfTheCount() {
        String ticket = unique("T-CUT-");
        folio(ticket, 300_000, "OPEN");
        ctx(ticket);
        long branch = newBranch();
        cashIn(ticket, 100_000, branch);
        closeDay(branch, 90_000, "Short 10,000");
        var pending = closes.findByBranchIdAndBusinessDay(branch, TODAY).orElseThrow();
        assertThat(pending.getStatus().name()).isEqualTo("PENDING_MANAGER_CONFIRMATION");

        String late = cashIn(ticket, 50_000, branch);
        assertThat(collections.findByCollectionReference(late).orElseThrow().getCashBusinessDay())
                .isEqualTo(TODAY.plusDays(1));

        var confirmed = collectionApi.confirmCashDifference(branch, TODAY, "CORR", MANAGER);
        assertThat(confirmed.status()).isEqualTo("CLOSED");
        assertThat(confirmed.countedCashVnd()).isEqualTo(90_000);
        assertThat(confirmed.expectedCashVnd()).isEqualTo(100_000);
        assertThat(cashClose.expectedCash(branch, TODAY)).isEqualTo(100_000);
        assertThat(cashClose.expectedCash(branch, TODAY.plusDays(1))).isEqualTo(50_000);
    }

    @Test
    @DisplayName("cashDayClosed is true from the submission, for PENDING_MANAGER_CONFIRMATION and CLOSED")
    void cashDayClosedFlagFromSubmission() {
        String pendingTicket = unique("T-FLAGP-");
        SettlementFinalBill pendingFolio = folio(pendingTicket, 100_000, "OPEN");
        ctx(pendingTicket);
        long branchP = newBranch();
        cashIn(pendingTicket, 100_000, branchP);
        assertThat(folioApi.toDto(pendingFolio, true).collections().get(0).cashDayClosed()).isFalse();
        closeDay(branchP, 90_000, "Short");
        assertThat(folioApi.toDto(pendingFolio, true).collections().get(0).cashDayClosed()).isTrue();

        String closedTicket = unique("T-FLAGC-");
        SettlementFinalBill closedFolio = folio(closedTicket, 100_000, "OPEN");
        ctx(closedTicket);
        long branchC = newBranch();
        cashIn(closedTicket, 100_000, branchC);
        closeDay(branchC, 100_000, null);
        assertThat(folioApi.toDto(closedFolio, true).collections().get(0).cashDayClosed()).isTrue();
    }

    @Test
    @DisplayName("refusals: Staff 403, open day, FINALIZED or CLOSED folio, reversed, under refund, unknown")
    void refusals() {
        String ticket = unique("T-REF-");
        folio(ticket, 600_000, "OPEN");
        ctx(ticket);
        long branch = newBranch();
        String open = cashIn(ticket, 100_000, branch);

        CurrentAccountActor staff = new CurrentAccountActor(301L, ActorRole.STAFF, ActorStatus.ACTIVE, true,
                List.of(branch));
        assertThatThrownBy(() -> reversalService.reverse(ticket, open, key(), "C", "r", staff))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class).hasMessage("MANAGER_REQUIRED");
        assertThatThrownBy(() -> reversalService.reverse(ticket, open, key(), "C", "r", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("CASH_DAY_NOT_CLOSED");
        assertThatThrownBy(() -> reversalService.reverse(ticket, open, null, "C", "r", MANAGER))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        assertThatThrownBy(() -> reversalService.reverse(ticket, open, key(), "C", " ", MANAGER))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        assertThatThrownBy(() -> reversalService.reverse(ticket, "NOPE-1", key(), "C", "r", MANAGER))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);

        // cash above the balance leaves money owed back (refund request), reversal refused
        String ticket2 = unique("T-REFUND-");
        folio(ticket2, 100_000, "OPEN");
        ctx(ticket2);
        String excess = cashIn(ticket2, 150_000, branch);
        closeDay(branch, cashClose.expectedCash(branch, TODAY), null);
        // the overpayment settled the folio; reopen it so the refund request is the only reason left
        var settled = bills.findByBookingReference(ticket2).orElseThrow();
        settled.setLifecycleStatus("OPEN");
        bills.saveAndFlush(settled);
        assertThatThrownBy(() -> reversalService.reverse(ticket2, excess, key(), "C", "r", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("COLLECTION_UNDER_REFUND");

        // already reversed
        reversalService.reverse(ticket, open, key(), "C", "wrong", MANAGER);
        assertThatThrownBy(() -> reversalService.reverse(ticket, open, key(), "C", "again", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("COLLECTION_ALREADY_REVERSED");

        // a FINALIZED folio is refused (folio correction and bank refund apply instead)
        var folioRow = bills.findByBookingReference(ticket).orElseThrow();
        folioRow.setLifecycleStatus("FINALIZED");
        bills.saveAndFlush(folioRow);
        String finalizedTicket = unique("T-FIN-");
        SettlementFinalBill fin = folio(finalizedTicket, 100_000, "OPEN");
        ctx(finalizedTicket);
        long branch2 = newBranch();
        String finCash = cashIn(finalizedTicket, 100_000, branch2);
        closeDay(branch2, 100_000, null);
        fin.setLifecycleStatus("FINALIZED");
        bills.saveAndFlush(fin);
        assertThatThrownBy(() -> reversalService.reverse(finalizedTicket, finCash, key(), "C", "r", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("FINAL_BILL_NOT_OPEN");
        fin.setLifecycleStatus("CLOSED");
        bills.saveAndFlush(fin);
        assertThatThrownBy(() -> reversalService.reverse(finalizedTicket, finCash, key(), "C", "r", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("FINAL_BILL_NOT_OPEN");
    }

    @Test
    @DisplayName("a transfer collection is not reversible as cash")
    void nonCashRefused() {
        String ticket = unique("T-NC-");
        folio(ticket, 200_000, "OPEN");
        ctx(ticket);
        String transfer = collectionService.recordBankTransfer(ticket, 100_000, "BANK-" + unique("X"), "payer", 300L,
                "CORR").collectionReference();
        assertThatThrownBy(() -> reversalService.reverse(ticket, transfer, key(), "C", "r", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("COLLECTION_NOT_CASH");
    }

    @Test
    @DisplayName("replay by Idempotency-Key returns the stored reversal; a changed reuse is IDEMPOTENCY_CONFLICT")
    void replayAndConflict() {
        String ticket = unique("T-IDEM-");
        folio(ticket, 200_000, "OPEN");
        ctx(ticket);
        long branch = newBranch();
        String collection = cashIn(ticket, 100_000, branch);
        closeDay(branch, 100_000, null);
        String key = key();

        CashReversalDto first = reversalService.reverse(ticket, collection, key, "C", "Counted twice", MANAGER);
        CashReversalDto replay = reversalService.reverse(ticket, collection, key, "C", "Counted twice", MANAGER);

        assertThat(replay.reversalReference()).isEqualTo(first.reversalReference());
        assertThat(reversals.findByCollectionId(collections.findByCollectionReference(collection).orElseThrow()
                .getId())).isPresent();
        assertThat(balanceDue(ticket)).isEqualTo(200_000);
        assertThatThrownBy(() -> reversalService.reverse(ticket, collection, key, "C", "A different reason", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("IDEMPOTENCY_CONFLICT");
    }
}
