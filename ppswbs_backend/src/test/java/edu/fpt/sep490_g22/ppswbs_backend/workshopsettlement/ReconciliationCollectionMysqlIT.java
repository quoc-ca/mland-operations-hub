package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingTimePayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingTimePaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashDayCloseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.ManagerReasonRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.Method;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.ReconciliationCollectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.State;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * 003.3 T133 (FR-056, SC-013): the Manager's reconciliation list on the Flyway-built MySQL schema. Real services and
 * repositories run; only the other modules' facades are mocked.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {
        "vnpay.merchant-api-ip-address=127.0.0.1",
        "vnpay.merchant-api-created-by=it-merchant",
        "workshop.operations.visible-location-ids="})
class ReconciliationCollectionMysqlIT {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.now(VIETNAM);
    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(300L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private static final CurrentAccountActor STAFF =
            new CurrentAccountActor(301L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of());

    @MockitoBean PaymentGatewayCheckoutPort checkout;
    @MockitoBean VnpayMerchantOperationsPort merchantOperations;
    @MockitoBean WorkshopOperationsFacade operationsFacade;
    @MockitoBean LoyaltyFacade loyalty;
    @MockitoBean WorkshopBookingFacade bookingFacade;
    @MockitoBean edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade bookingNotifications;

    @Autowired SettlementFinalBillRepository bills;
    @Autowired SettlementFolioCollectionRepository collections;
    @Autowired SettlementCashDayCloseRepository closes;
    @Autowired SettlementEvidenceConclusionRepository evidence;
    @Autowired FolioCollectionService collectionService;
    @Autowired TicketFolioApiService folioApi;
    @Autowired ReconciliationCollectionService reconciliation;
    @Autowired CollectionBranchBackfillService backfill;
    @Autowired FolioBalanceService balances;
    @Autowired BankRefundService bankRefunds;
    @Autowired JdbcTemplate jdbc;

    private static String unique(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }

    private static long newBranch() {
        return 9_400_000L + SEQ.incrementAndGet();
    }

    private static String key() {
        return "it-idempotency-key-" + UUID.randomUUID();
    }

    private SettlementFinalBill folio(String ticket, long remaining) {
        return bills.save(SettlementFinalBill.builder().billReference("BILL-" + ticket).bookingReference(ticket)
                .groupReference("G-" + ticket).postPromotionAmountVnd(remaining).acceptedDepositAmountVnd(0L)
                .remainingAmountVnd(remaining).currencyCode("VND").lifecycleStatus("OPEN").build());
    }

    private void ctx(String ticket, long location) {
        doReturn(FinalBalanceResolutionOutcome.accepted()).when(bookingFacade).recordTicketFinalBalanceResolved(any());
        doReturn(Optional.of(new TicketFolioContext(ticket, "G-" + ticket, "G-" + ticket + "-L1", "7", location,
                List.of(), null, null, Instant.now().plus(72, ChronoUnit.HOURS), 0L, "L1-" + ticket, 0L)))
                .when(bookingFacade).getTicketFolioContext(ticket);
    }

    private String transfer(String ticket, long amount) {
        return collectionService.recordBankTransfer(ticket, amount, "BANK-" + unique("X"), "payer", 300L, "CORR")
                .collectionReference();
    }

    private List<ReconciliationCollectionDto> list(long branch, Method method, State state) {
        return reconciliation.list(branch, TODAY, method, state, MANAGER);
    }

    private static List<String> ids(List<ReconciliationCollectionDto> rows) {
        return rows.stream().map(ReconciliationCollectionDto::collectionId).toList();
    }

    @Test
    @DisplayName("collections are stamped with the ticket branch at creation, transfer and VNPay")
    void newCollectionsCarryTheirBranch() {
        long branch = newBranch();
        String ticket = unique("T-STAMP-");
        folio(ticket, 300_000);
        ctx(ticket, branch);
        when(checkout.createFinalPaymentUrl(any(), any(), anyLong(), any(), any(), any())).thenReturn("https://pay.example.invalid/c");

        String bank = transfer(ticket, 100_000);
        assertThat(collections.findByCollectionReference(bank).orElseThrow().getBranchId()).isEqualTo(branch);

        String vnpay = collectionService.recordGatewayAttempt(ticket, 100_000, "127.0.0.1", 300L, "CORR")
                .collectionReference();
        assertThat(collections.findByCollectionReference(vnpay).orElseThrow().getBranchId()).isEqualTo(branch);
    }

    @Test
    @DisplayName("exact filtering by branch, day, method and state; nothing is written")
    void filtersByBranchDayMethodAndState() {
        long branch = newBranch();
        long otherBranch = newBranch();
        String ticket = unique("T-F-");
        folio(ticket, 1_000_000);
        ctx(ticket, branch);
        String otherTicket = unique("T-FO-");
        folio(otherTicket, 1_000_000);
        ctx(otherTicket, otherBranch);
        when(checkout.createFinalPaymentUrl(any(), any(), anyLong(), any(), any(), any())).thenReturn("https://pay.example.invalid/c");

        String confirmed = transfer(ticket, 100_000);
        collectionService.confirmBankTransfer(confirmed, 300L, "CORR");
        String pending = transfer(ticket, 200_000);
        String voided = transfer(ticket, 300_000);
        folioApi.voidCollection(ticket, voided, key(), "CORR", new ManagerReasonRequest("wrong"), MANAGER);
        String elsewhere = transfer(otherTicket, 100_000);
        // a pending row created yesterday belongs to yesterday, not today
        String yesterdayRow = transfer(ticket, 10_000);
        jdbc.update("update settlement_folio_collections set created_at = ? where collection_reference = ?",
                java.sql.Timestamp.from(TODAY.minusDays(1).atTime(12, 0).atZone(VIETNAM).toInstant()), yesterdayRow);
        String gateway = collectionService.recordGatewayAttempt(ticket, 50_000, "127.0.0.1", 300L, "CORR")
                .collectionReference();

        long foliosBefore = bills.count();
        long collectionsBefore = collections.count();
        long closesBefore = closes.count();
        long dueBefore = balances.balanceOf(bills.findByBookingReference(ticket).orElseThrow()).balanceDue();

        assertThat(ids(list(branch, null, null))).containsExactlyInAnyOrder(confirmed, pending, voided, gateway);
        assertThat(ids(list(otherBranch, null, null))).containsExactly(elsewhere);
        assertThat(ids(list(branch, Method.BANK_TRANSFER, null))).containsExactlyInAnyOrder(confirmed, pending, voided);
        assertThat(ids(list(branch, Method.VNPAY, null))).containsExactly(gateway);
        assertThat(ids(list(branch, null, State.CONFIRMED))).containsExactly(confirmed);
        assertThat(ids(list(branch, null, State.PENDING_VERIFICATION))).containsExactlyInAnyOrder(pending, gateway);
        assertThat(ids(list(branch, null, State.VOIDED))).containsExactly(voided);
        assertThat(ids(list(branch, Method.BANK_TRANSFER, State.PENDING_VERIFICATION))).containsExactly(pending);
        assertThat(reconciliation.list(branch, TODAY.minusDays(1), null, null, MANAGER))
                .extracting(ReconciliationCollectionDto::collectionId).containsExactly(yesterdayRow);
        assertThat(reconciliation.list(branch, TODAY.plusDays(1), null, null, MANAGER)).isEmpty();

        ReconciliationCollectionDto row = list(branch, null, State.CONFIRMED).get(0);
        assertThat(row.ticketCode()).isEqualTo(ticket);
        assertThat(row.groupCode()).isEqualTo("G-" + ticket);
        assertThat(row.amount()).isEqualTo("100000");
        assertThat(row.method()).isEqualTo(Method.BANK_TRANSFER);
        assertThat(row.confirmedBy()).isEqualTo("300");
        assertThat(row.confirmedAt()).isNotNull();
        assertThat(row.locationIds()).containsExactly(String.valueOf(branch));

        assertThat(bills.count()).isEqualTo(foliosBefore);
        assertThat(collections.count()).isEqualTo(collectionsBefore);
        assertThat(closes.count()).isEqualTo(closesBefore);
        assertThat(balances.balanceOf(bills.findByBookingReference(ticket).orElseThrow()).balanceDue())
                .isEqualTo(dueBefore);
    }

    @Test
    @DisplayName("Staff is refused; a transfer beyond the balance carries excessAmount and its refund request")
    void staffRefusedAndExcessRow() {
        long branch = newBranch();
        assertThatThrownBy(() -> reconciliation.list(branch, TODAY, null, null, STAFF))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);

        String ticket = unique("T-X-");
        folio(ticket, 500_000);
        ctx(ticket, branch);
        String transferRef = transfer(ticket, 400_000);
        collectionService.confirmBankTransfer(transferRef, 300L, "CORR");
        jdbc.update("update settlement_folio_collections set excess_vnd = 150000 where collection_reference = ?",
                transferRef);
        var refund = bankRefunds.open(ticket, "G-" + ticket, RefundReason.EXCESS_OR_DUPLICATE_MONEY, transferRef,
                150_000, "CORR");

        ReconciliationCollectionDto row = list(branch, Method.BANK_TRANSFER, null).get(0);
        assertThat(row.amount()).isEqualTo("400000");
        assertThat(row.excessAmount()).isEqualTo("150000");
        assertThat(row.refundRequestReference()).isEqualTo(refund.request().getRequestReference());
    }

    @Test
    @DisplayName("EXCESS rows come from FOLIO_VNPAY_EXCESS evidence of the ticket's branch on the evidence day")
    void excessRowsByTicketBranch() {
        long branch = newBranch();
        long otherBranch = newBranch();
        String mine = unique("T-EX-");
        String other = unique("T-EXO-");
        ctx(mine, branch);
        ctx(other, otherBranch);
        for (String ticket : List.of(mine, other)) {
            evidence.save(SettlementEvidenceConclusion.builder().evidenceReference("EVID-" + ticket)
                    .bookingReference(ticket).providerTransactionReference("VNP-" + ticket)
                    .conclusion("FOLIO_VNPAY_EXCESS").amountVnd(70_000L).currencyCode("VND")
                    .operationReference("OP-" + ticket).createdAt(Instant.now()).build());
        }

        List<ReconciliationCollectionDto> rows = list(branch, null, State.EXCESS);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).ticketCode()).isEqualTo(mine);
        assertThat(rows.get(0).state()).isEqualTo(State.EXCESS);
        assertThat(rows.get(0).method()).isEqualTo(Method.VNPAY);
        assertThat(rows.get(0).collectionId()).isNull();
        assertThat(rows.get(0).amount()).isEqualTo("70000");
        assertThat(list(branch, Method.BANK_TRANSFER, State.EXCESS)).isEmpty();
        assertThat(list(branch, null, State.CONFIRMED)).isEmpty();
    }

    @Test
    @DisplayName("BOOKING_TIME: online 6,000,000 + counter 1,200,000 = 7,200,000; 003.1 failing is a 503 for the whole list")
    void bookingTimeRowsAndSourceFailure() {
        long branch = newBranch();
        String ticket = unique("T-BT-");
        folio(ticket, 1_200_000);
        ctx(ticket, branch);
        String counter = transfer(ticket, 1_200_000);
        collectionService.confirmBankTransfer(counter, 300L, "CORR");
        when(bookingFacade.listBookingTimePayments(branch, TODAY)).thenReturn(List.of(
                new BookingTimePayment("GRP-ON", List.of("T-A", "T-B", "T-C"), 6_000_000L,
                        BookingTimePayment.METHOD_VNPAY_ONLINE, BookingTimePaymentStatus.CONFIRMED, "MREF-1", "ATT-1",
                        Instant.now(), Instant.now(), List.of(branch, branch + 1)),
                new BookingTimePayment("GRP-UNV", List.of("T-D"), 900_000L, BookingTimePayment.METHOD_VNPAY_ONLINE,
                        BookingTimePaymentStatus.UNVERIFIED, "MREF-2", "ATT-2", Instant.now(), null, List.of(branch)),
                new BookingTimePayment("GRP-LATE", List.of("T-E"), 800_000L, BookingTimePayment.METHOD_VNPAY_ONLINE,
                        BookingTimePaymentStatus.LATE_PENDING_REVIEW, "MREF-3", "ATT-3", Instant.now(), null,
                        List.of(branch))));

        List<ReconciliationCollectionDto> confirmed = list(branch, null, State.CONFIRMED);
        long total = confirmed.stream().mapToLong(row -> Long.parseLong(row.amount())).sum();
        assertThat(total).isEqualTo(7_200_000L);
        assertThat(confirmed).extracting(ReconciliationCollectionDto::method)
                .containsExactlyInAnyOrder(Method.BANK_TRANSFER, Method.BOOKING_TIME);

        List<ReconciliationCollectionDto> online = list(branch, Method.BOOKING_TIME, null);
        assertThat(online).hasSize(3);
        ReconciliationCollectionDto multi = online.stream().filter(r -> "GRP-ON".equals(r.groupCode())).findFirst().orElseThrow();
        assertThat(multi.ticketCode()).isNull();
        assertThat(multi.ticketCodes()).containsExactly("T-A", "T-B", "T-C");
        assertThat(multi.locationIds()).containsExactly(String.valueOf(branch), String.valueOf(branch + 1));
        assertThat(online.stream().filter(r -> r.state() == State.PENDING_VERIFICATION)).hasSize(2);
        assertThat(online.stream().map(ReconciliationCollectionDto::note).filter(java.util.Objects::nonNull))
                .containsExactly("late payment under review");

        when(bookingFacade.listBookingTimePayments(branch, TODAY)).thenThrow(new IllegalStateException("003.1 down"));
        assertThatThrownBy(() -> list(branch, null, null)).isInstanceOf(ReconciliationSourceUnavailableException.class);
        assertThatThrownBy(() -> list(branch, Method.BOOKING_TIME, null))
                .isInstanceOf(ReconciliationSourceUnavailableException.class);
        assertThat(list(branch, Method.BANK_TRANSFER, null)).hasSize(1);
    }

    @Test
    @DisplayName("back-fill: old transfer/VNPAY rows get their branch from the ticket context; unresolved stay NULL")
    void backfillFillsResolvedAndListsUnresolved() {
        long branch = newBranch();
        String ticket = unique("T-BF-");
        folio(ticket, 300_000);
        ctx(ticket, branch);
        String filled = transfer(ticket, 100_000);
        String ghostTicket = unique("T-GHOST-");
        folio(ghostTicket, 300_000);
        ctx(ghostTicket, branch);
        String ghost = transfer(ghostTicket, 100_000);
        jdbc.update("update settlement_folio_collections set branch_id = NULL where collection_reference in (?, ?)",
                filled, ghost);
        when(bookingFacade.getTicketFolioContext(ghostTicket)).thenReturn(Optional.empty());

        var result = backfill.backfill();

        assertThat(collections.findByCollectionReference(filled).orElseThrow().getBranchId()).isEqualTo(branch);
        assertThat(collections.findByCollectionReference(ghost).orElseThrow().getBranchId()).isNull();
        assertThat(result.unresolved()).anyMatch(entry -> entry.contains(ghost));
        assertThat(result.unresolved()).noneMatch(entry -> entry.contains(filled));
        assertThat(result.filled()).isGreaterThanOrEqualTo(1);
        // idempotent: the filled row is not touched again
        assertThat(backfill.backfill().unresolved()).anyMatch(entry -> entry.contains(ghost));
        assertThat(collections.findByCollectionReference(filled).orElseThrow().getMethod())
                .isEqualTo(FolioCollectionMethod.BANK_TRANSFER);
    }
}
