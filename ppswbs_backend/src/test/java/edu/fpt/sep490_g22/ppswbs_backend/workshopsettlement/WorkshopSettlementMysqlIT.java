package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingGroupLoyaltyOutcomeSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConsumeBuyerProofResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketBookingRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.EquivalentReplacementSessions;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketCancellationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketSessionReplacementOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundAllocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketMlandRemedyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.CashCloseRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.CashCloseResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCorrectionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketCancellationDtos.CancellationRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketCancellationDtos.TicketCancellationDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDecisionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GroupTicketChangeItemDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GroupTicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.TicketRemedyAnswer;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.TicketRemedyAnswerRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketMlandRemedy;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BankRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.CashDayCloseService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCollectionApiService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCollectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCorrectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.GroupTicketChangeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundAccountCipher;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SessionCancellationRemedyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeRequestService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketMlandRemedyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketSettlementCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 003.3 T103: MySQL acceptance of the settlement services of Phase 8 groups A (parts 1 and 2). Real services and
 * repositories run on the Flyway-built schema; only the other modules' facades and the payment ports are mocked.
 * Covers User Stories 5-10, SC-002a, SC-004a, SC-006 to SC-012.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {
        "vnpay.merchant-api-ip-address=127.0.0.1",
        "vnpay.merchant-api-created-by=it-merchant",
        "workshop.operations.visible-location-ids="})
class WorkshopSettlementMysqlIT {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final String KEY_PREFIX = "it-idempotency-key-";
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
    @Autowired SettlementRefundRequestRepository refunds;
    @Autowired SettlementRefundAllocationRepository allocations;
    @Autowired SettlementTicketMlandRemedyRepository remedies;
    @Autowired SettlementTicketChangeRepository changes;
    @Autowired SettlementTicketChangeRequestRepository changeRequests;
    @Autowired FolioCollectionService collectionService;
    @Autowired FolioCollectionApiService collectionApi;
    @Autowired CashDayCloseService cashClose;
    @Autowired BankRefundService bankRefunds;
    @Autowired FolioCorrectionService corrections;
    @Autowired TicketSettlementCancellationService cancellations;
    @Autowired TicketMlandRemedyService remedyService;
    @Autowired SessionCancellationRemedyService sessionRemedies;
    @Autowired TicketChangeService changeService;
    @Autowired TicketChangeRequestService changeRequestService;
    @Autowired GroupTicketChangeService groupChangeService;

    @BeforeAll
    static void refundAccountKey() {
        ReflectionTestUtils.invokeMethod(RefundAccountCipher.class, "useKeyForTests",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    @BeforeEach
    void commonStubs() {
        doReturn(FinalBalanceResolutionOutcome.accepted()).when(bookingFacade).recordTicketFinalBalanceResolved(any());
        doReturn(ConsumeBuyerProofResult.accepted()).when(bookingFacade).consumeBuyerProof(any());
        when(checkout.createFinalPaymentUrl(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), any(), any(),
                anyString())).thenReturn("https://pay.example.invalid/checkout");
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private static String unique(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }

    private static String key() {
        return KEY_PREFIX + UUID.randomUUID();
    }

    private SettlementFinalBill folio(String ticket, String group, long deposit, long remaining, String status) {
        return bills.save(SettlementFinalBill.builder().billReference("BILL-" + ticket).bookingReference(ticket)
                .groupReference(group).postPromotionAmountVnd(deposit + remaining).acceptedDepositAmountVnd(deposit)
                .remainingAmountVnd(remaining).currencyCode("VND").lifecycleStatus(status).build());
    }

    private TicketFolioContext ctx(String ticket, String group, Long buyer, UUID opsRef, Instant firstStart,
                                   long voucher, List<String> sessions) {
        TicketFolioContext context = new TicketFolioContext(ticket, group, group + "-L1", "7", 10L, sessions, buyer,
                opsRef == null ? null : opsRef.toString(), firstStart, 0L, "L1-" + ticket, voucher);
        doReturn(Optional.of(context)).when(bookingFacade).getTicketFolioContext(ticket);
        return context;
    }

    private long balanceOwed(String ticket) {
        return bills.findByBookingReference(ticket).orElseThrow().getRemainingAmountVnd();
    }

    private String status(String ticket) {
        return bills.findByBookingReference(ticket).orElseThrow().getLifecycleStatus();
    }

    private void walkRefundToReconciliation(SettlementRefundRequest request) {
        bankRefunds.submitAccountAtCounter(request.getRequestReference(), "VCB", "0123456789", "NGUYEN VAN A", 300L, "CORR");
        bankRefunds.approve(request.getRequestReference(), 300L, "Checked against the ticket", "CORR");
        bankRefunds.recordTransfer(request.getRequestReference(), 300L, "BANK-TXN-" + SEQ.incrementAndGet(),
                "PROOF-" + SEQ.get(), "CORR");
    }

    // ---- US5: collections ---------------------------------------------------------------------------------

    @Test
    @DisplayName("mixed collection: cash, then transfer, then VNPay on one folio; VNPay under 5,000 is refused")
    void mixedCollectionAndVnpayMinimum() {
        String ticket = unique("T-MIX-");
        folio(ticket, "G-MIX", 0, 300_000, "OPEN");
        ctx(ticket, "G-MIX", null, null, Instant.now().plus(72, ChronoUnit.HOURS), 0, List.of());
        long branch = 9_000_000L + SEQ.incrementAndGet();

        var cash = collectionService.collectCash(ticket, 100_000L, 120_000L, "RCPT-" + ticket, "payer", branch, 300L, "CORR");
        assertThat(cash.status()).isEqualTo(FolioCollectionStatus.CONFIRMED);
        assertThat(cash.balanceDue()).isEqualTo(200_000L);

        var transfer = collectionService.recordBankTransfer(ticket, 100_000L, "TXN-" + ticket, "payer", 300L, "CORR");
        assertThat(transfer.status()).isEqualTo(FolioCollectionStatus.PENDING_VERIFICATION);

        assertThatThrownBy(() -> collectionService.recordGatewayAttempt(ticket, 4_999L, "127.0.0.1", 300L, "CORR"))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessageContaining("VNPAY_BELOW_MINIMUM");

        var gateway = collectionService.recordGatewayAttempt(ticket, 5_000L, "127.0.0.1", 300L, "CORR");
        assertThat(gateway.status()).isEqualTo(FolioCollectionStatus.PENDING_VERIFICATION);
        assertThat(collections.findByBillReferenceOrderByIdAsc("BILL-" + ticket)).hasSize(3);
        assertThat(status(ticket)).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("transfer confirmation settles the right ticket folio, replays are refused, a duplicate bank reference is refused")
    void bankTransferConfirmationAndDuplicateReference() {
        String ticketA = unique("T-BNK-A-");
        String ticketB = unique("T-BNK-B-");
        folio(ticketA, "G-BNK", 0, 80_000, "OPEN");
        folio(ticketB, "G-BNK", 0, 80_000, "OPEN");
        ctx(ticketA, "G-BNK", null, null, Instant.now().plus(72, ChronoUnit.HOURS), 0, List.of());
        ctx(ticketB, "G-BNK", null, null, Instant.now().plus(72, ChronoUnit.HOURS), 0, List.of());
        String reference = "BANK-" + SEQ.incrementAndGet();

        var pending = collectionService.recordBankTransfer(ticketA, 80_000L, reference, "payer", 300L, "CORR");
        assertThatThrownBy(() -> collectionService.recordBankTransfer(ticketB, 80_000L, reference, "payer", 300L, "CORR"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessageContaining("COLLECTION_ALREADY_RECORDED");

        var confirmed = collectionService.confirmBankTransfer(pending.collectionReference(), 300L, "CORR");
        assertThat(confirmed.status()).isEqualTo(FolioCollectionStatus.CONFIRMED);
        assertThat(status(ticketA)).isEqualTo("FINALIZED");
        assertThat(status(ticketB)).isEqualTo("OPEN");
        assertThatThrownBy(() -> collectionService.confirmBankTransfer(pending.collectionReference(), 300L, "CORR"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessageContaining("COLLECTION_NOT_PENDING");
    }

    @Test
    @DisplayName("excess cash is kept in full and opens an owed-back bank refund")
    void excessMoneyOpensAnOwedRefund() {
        String ticket = unique("T-EXC-");
        folio(ticket, "G-EXC", 0, 50_000, "OPEN");
        ctx(ticket, "G-EXC", null, null, Instant.now().plus(72, ChronoUnit.HOURS), 0, List.of());

        var outcome = collectionService.collectCash(ticket, 70_000L, 70_000L, "RCPT-" + ticket, "payer",
                9_100_000L + SEQ.incrementAndGet(), 300L, "CORR");

        assertThat(outcome.excessVnd()).isEqualTo(20_000L);
        assertThat(status(ticket)).isEqualTo("FINALIZED");
        var owed = refunds.findByReasonAndSourceReference(RefundReason.EXCESS_OR_DUPLICATE_MONEY,
                outcome.collectionReference());
        assertThat(owed).isPresent();
        assertThat(owed.get().getAmountVnd()).isEqualTo(20_000L);
        assertThat(owed.get().getStatus().name()).isEqualTo("PENDING_ACCOUNT");
    }

    // ---- cash day close ----------------------------------------------------------------------------------

    @Test
    @DisplayName("cash day close: a difference needs a reason, replays by Idempotency-Key, a Manager confirms it")
    void cashDayCloseDifferenceAndReplay() {
        String ticket = unique("T-CASH-");
        folio(ticket, "G-CASH", 0, 200_000, "OPEN");
        ctx(ticket, "G-CASH", null, null, Instant.now().plus(72, ChronoUnit.HOURS), 0, List.of());
        long branch = 9_200_000L + SEQ.incrementAndGet();
        collectionService.collectCash(ticket, 120_000L, 120_000L, "RCPT-" + ticket, "payer", branch, 300L, "CORR");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(cashClose.expectedCash(branch, today)).isEqualTo(120_000L);

        assertThatThrownBy(() -> collectionApi.closeCashDay(new CashCloseRequest(branch, today, 100_000L, null), key(), "CORR", MANAGER))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessageContaining("CASH_DIFFERENCE_REASON_REQUIRED");

        String closeKey = key();
        CashCloseRequest request = new CashCloseRequest(branch, today, 100_000L, "Counted 20,000 short");
        CashCloseResponse closed = collectionApi.closeCashDay(request, closeKey, "CORR", MANAGER);
        assertThat(closed.differenceVnd()).isEqualTo(-20_000L);
        assertThat(closed.status()).isEqualTo("PENDING_MANAGER_CONFIRMATION");
        assertThat(collectionApi.closeCashDay(request, closeKey, "CORR", MANAGER).closeReference())
                .isEqualTo(closed.closeReference());
        assertThatThrownBy(() -> collectionApi.closeCashDay(request, key(), "CORR", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessageContaining("CASH_DAY_ALREADY_CLOSED");

        CashCloseResponse confirmed = collectionApi.confirmCashDifference(branch, today, "CORR", MANAGER);
        assertThat(confirmed.status()).isEqualTo("CLOSED");
    }

    // ---- US7: refund lifecycle and Mland remedy folio -----------------------------------------------------

    @Test
    @DisplayName("a Mland refund closes the folio only when the bank refund is REFUNDED; points and voucher come back once")
    void mlandRefundClosesFolioOnlyWhenRefunded() {
        String group = unique("G-MR-");
        String ticket = unique("T-MR-");
        UUID opsRef = UUID.randomUUID();
        folio(ticket, group, 90_000, 0, "OPEN");
        ctx(ticket, group, 100L, opsRef, Instant.now().plus(72, ChronoUnit.HOURS), 0, List.of());
        when(operationsFacade.cancelTicket(any())).thenReturn(TicketCancellationOutcome.accepted(opsRef, List.of()));

        SettlementTicketMlandRemedy remedy = remedyService.openDirectRefund(ticket, UUID.randomUUID().toString(), "CORR");

        assertThat(remedy.getOutcome()).isEqualTo("REFUND");
        SettlementRefundRequest refund = refunds.findByRequestReference(remedy.getRefundRequestReference()).orElseThrow();
        assertThat(refund.getAmountVnd()).isEqualTo(90_000L);
        assertThat(status(ticket)).as("money still has to go back").isEqualTo("OPEN");

        walkRefundToReconciliation(refund);
        assertThat(status(ticket)).isEqualTo("OPEN");
        bankRefunds.confirmRefunded(refund.getRequestReference(), 300L, "CORR");
        assertThat(status(ticket)).isEqualTo("CLOSED");
        bankRefunds.confirmRefunded(refund.getRequestReference(), 300L, "CORR");
        assertThat(status(ticket)).isEqualTo("CLOSED");
    }

    // ---- correction cap (V146) ---------------------------------------------------------------------------

    @Test
    @DisplayName("a correction is capped by the money really collected, and cash/booking-time allocations carry no provider facts (V146)")
    void correctionIsCappedByRealMoney() {
        String ticket = unique("T-COR-");
        folio(ticket, "G-COR", 100_000, 0, "FINALIZED");
        ctx(ticket, "G-COR", null, null, Instant.now().plus(72, ChronoUnit.HOURS), 0, List.of());

        FolioCorrectionDtos.Response first = corrections.create(ticket, key(), "CORR",
                new FolioCorrectionDtos.CreateRequest(60_000L, "Overcharged at the counter"), MANAGER);
        assertThat(first).isNotNull();
        assertThat(allocations.findAll()).anySatisfy(allocation -> {
            if (allocation.getOriginalPaymentReference().equals("BOOKING_TIME:" + ticket)) {
                assertThat(allocation.getAllocatedAmountVnd()).isEqualTo(60_000L);
                assertThat(allocation.getOriginalProviderTransactionNumber()).isNull();
                assertThat(allocation.getOriginalProviderCreateDate()).isNull();
            }
        });
        assertThatThrownBy(() -> corrections.create(ticket, key(), "CORR",
                new FolioCorrectionDtos.CreateRequest(10_000L, "Second correction while the first is open"), MANAGER))
                .hasMessageContaining("FOLIO_CORRECTION_ALREADY_IN_PROGRESS");

        String other = unique("T-COR2-");
        folio(other, "G-COR", 100_000, 0, "FINALIZED");
        ctx(other, "G-COR", null, null, Instant.now().plus(72, ChronoUnit.HOURS), 0, List.of());
        assertThatThrownBy(() -> corrections.create(other, key(), "CORR",
                new FolioCorrectionDtos.CreateRequest(100_001L, "More than was ever collected"), MANAGER))
                .hasMessageContaining("OVERCHARGE_EXCEEDS_UNREFUNDED_COLLECTIONS");
    }

    // ---- US8: cancellation 24 h and voucher once ----------------------------------------------------------

    @Test
    @DisplayName("cancellation: 24 h 00 m before the session keeps the benefit, 23 h 59 m does not; the voucher comes back once")
    void cancellationBoundaryAndVoucherOnce() {
        String group = unique("G-CAN-");
        String valid = unique("T-CAN-V-");
        String late = unique("T-CAN-L-");
        folio(valid, group, 50_000, 0, "OPEN");
        folio(late, group, 50_000, 0, "OPEN");
        ctx(valid, group, 100L, null, Instant.now().plus(24, ChronoUnit.HOURS).plus(2, ChronoUnit.MINUTES), 0, List.of());
        ctx(late, group, 100L, null, Instant.now().plus(23, ChronoUnit.HOURS).plus(59, ChronoUnit.MINUTES), 0, List.of());
        doReturn(
                new BookingGroupLoyaltyOutcomeSource(true, "SNAP-" + group, 1, Instant.now().plus(7, ChronoUnit.DAYS), List.of())).when(bookingFacade).getGroupLoyaltyOutcomeSource(group);
        when(loyalty.findBenefitResult(SourceKind.WORKSHOP, group)).thenReturn(
                new BenefitResult(SourceKind.WORKSHOP, group, ComponentState.CONSUMED, ComponentState.CONSUMED, EarnState.PENDING, null),
                new BenefitResult(SourceKind.WORKSHOP, group, ComponentState.RESTORED, ComponentState.RESTORED, EarnState.INELIGIBLE, null));
        when(loyalty.restoreCancelledWorkshopBenefits(any())).thenReturn(
                new BenefitResult(SourceKind.WORKSHOP, group, ComponentState.RESTORED, ComponentState.RESTORED, EarnState.INELIGIBLE, null));
        when(loyalty.finishIneligibleEarn(any())).thenReturn(
                new BenefitResult(SourceKind.WORKSHOP, group, ComponentState.RESTORED, ComponentState.RESTORED, EarnState.INELIGIBLE, null));
        CurrentAccountActor buyer = new CurrentAccountActor(100L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

        TicketCancellationDto first = cancellations.cancelTicket(valid, new CancellationRequestDto("Plans changed", null), key(), "CORR", buyer);
        TicketCancellationDto second = cancellations.cancelTicket(late, new CancellationRequestDto("Plans changed", null), key(), "CORR", buyer);

        assertThat(first.benefitOutcome()).isEqualTo("VALID_CANCELLATION");
        assertThat(first.voucherRestored()).isTrue();
        assertThat(second.benefitOutcome()).isEqualTo("LATE_CANCELLATION");
        assertThat(second.pointsReturned()).isZero();
        assertThat(second.voucherRestored()).as("the group's voucher was already restored").isFalse();
        assertThat(status(valid)).isEqualTo("CLOSED");
        assertThatThrownBy(() -> cancellations.cancelTicket(valid, new CancellationRequestDto("again", null), key(), "CORR", buyer))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    // ---- US9: Mland partial cancellation ------------------------------------------------------------------

    @Test
    @DisplayName("Mland cancels one session of a group: offers list every session, the buyer picks one, silence means refund")
    void mlandPartialGroupCancellation() {
        String group = unique("G-ML-");
        String line = group + "-L1";
        UUID cancelled = UUID.randomUUID();
        UUID early = UUID.randomUUID();
        UUID late = UUID.randomUUID();
        Instant earlyStart = Instant.now().plus(60, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        Instant lateStart = Instant.now().plus(100, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        String chooser = unique("T-ML-1-");
        String silent = unique("T-ML-2-");
        String noOption = unique("T-ML-3-");
        String untouched = unique("T-ML-4-");
        UUID[] ops = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        String[] codes = {chooser, silent, noOption, untouched};
        for (int i = 0; i < 4; i++) {
            folio(codes[i], group, 40_000, 0, "OPEN");
            ctx(codes[i], group, 100L, ops[i], Instant.now().plus(40, ChronoUnit.HOURS), 0,
                    List.of(i == 3 ? UUID.randomUUID().toString() : cancelled.toString()));
            doReturn(Optional.of(new TicketBookingRef(codes[i], group))).when(bookingFacade).findTicketBookingByOperationsReference(ops[i].toString());
        }
        when(operationsFacade.getBookingTicketProjection(any())).thenReturn(new DeliverySafeTicketSet(List.of(
                projection(ops[0], chooser, cancelled), projection(ops[1], silent, cancelled),
                projection(ops[2], noOption, cancelled), projection(ops[3], untouched, UUID.randomUUID()))));
        when(operationsFacade.findEquivalentReplacementSessions(any())).thenAnswer(invocation -> {
            var query = (edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.FindEquivalentReplacementSessionsQuery)
                    invocation.getArgument(0);
            if (query.ticketReference().equals(ops[2])) {
                return EquivalentReplacementSessions.none();
            }
            return new EquivalentReplacementSessions(List.of(
                    new EquivalentReplacementSessions.Candidate(early, earlyStart, earlyStart.plus(2, ChronoUnit.HOURS)),
                    new EquivalentReplacementSessions.Candidate(late, lateStart, lateStart.plus(2, ChronoUnit.HOURS))));
        });
        when(operationsFacade.cancelTicket(any())).thenAnswer(invocation -> TicketCancellationOutcome.accepted(ops[2], List.of()));
        when(operationsFacade.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        doReturn(BookingGroupLoyaltyOutcomeSource.none()).when(bookingFacade).getGroupLoyaltyOutcomeSource(group);

        int opened = sessionRemedies.openRemedies(cancelled.toString(), List.of(line), "CORR-ML");
        assertThat(opened).as("the ticket that does not hold the cancelled session is left alone").isEqualTo(3);
        assertThat(sessionRemedies.openRemedies(cancelled.toString(), List.of(line), "CORR-ML")).isEqualTo(3);
        assertThat(remedies.findByTicketCodeOrderByCreatedAtDesc(chooser)).hasSize(1);

        SettlementTicketMlandRemedy offer = remedies.findByTicketCodeAndOutcome(chooser, "PENDING_ANSWER").orElseThrow();
        assertThat(offer.getReplacementSessionReferences()).contains(early.toString()).contains(late.toString());
        assertThat(offer.getAnswerDeadlineAt()).isEqualTo(lateStart.minus(24, ChronoUnit.HOURS));
        var listed = remedyService.getOffer(chooser, null);
        assertThat(listed.offeredSessions()).hasSize(2);

        var direct = remedies.findByTicketCodeOrderByCreatedAtDesc(noOption).get(0);
        assertThat(direct.getOutcome()).as("no equivalent session: refund at once").isEqualTo("REFUND");
        assertThat(remedies.findByTicketCodeOrderByCreatedAtDesc(untouched)).isEmpty();

        CurrentAccountActor buyer = new CurrentAccountActor(100L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        assertThatThrownBy(() -> remedyService.answerOffer(chooser, new TicketRemedyAnswerRequestDto(
                TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, UUID.randomUUID().toString()), key(), "CORR", buyer))
                .hasMessageContaining("REPLACEMENT_SESSION_NOT_OFFERED");
        var answered = remedyService.answerOffer(chooser, new TicketRemedyAnswerRequestDto(
                TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, late.toString()), key(), "CORR", buyer);
        assertThat(answered.outcome()).isEqualTo("MOVED");
        assertThat(answered.selectedReplacementSessionReference()).isEqualTo(late.toString());

        // Silence: the buyer never answers, so once the deadline (24 h before the latest session) passes it is refunded.
        SettlementTicketMlandRemedy stale = remedies.findByTicketCodeAndOutcome(silent, "PENDING_ANSWER").orElseThrow();
        assertThat(stale.getAnswerDeadlineAt()).isEqualTo(lateStart.minus(24, ChronoUnit.HOURS));
        stale.setAnswerDeadlineAt(Instant.now().minus(1, ChronoUnit.HOURS));
        remedies.save(stale);
        assertThat(remedyService.processUnansweredRemedies()).isGreaterThanOrEqualTo(1);
        SettlementTicketMlandRemedy refunded = remedies.findByRemedyReference(stale.getRemedyReference()).orElseThrow();
        assertThat(refunded.getOutcome()).isEqualTo("REFUND");
        assertThat(refunded.getRefundRequestReference()).isNotBlank();
        assertThat(refunded.getBuyerAnswer()).as("never moved on its own").isNull();
    }

    private static DeliverySafeTicket projection(UUID ticket, String code, UUID session) {
        return new DeliverySafeTicket(ticket, "qr-" + code, code, List.of(
                new SessionEntitlementProjection(session, 10L, Instant.now(), Instant.now())));
    }

    // ---- US10: change limits and price increase -----------------------------------------------------------

    @Test
    @DisplayName("change: the price increase is confirmed first, the one change is counted, the next waits for a Manager who approves it")
    void changeLimitsPriceIncreaseAndManagerQueue() {
        String group = unique("G-CHG-");
        String ticket = unique("T-CHG-");
        UUID opsRef = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        folio(ticket, group, 100_000, 100_000, "OPEN");
        ctx(ticket, group, 100L, opsRef, Instant.now().plus(72, ChronoUnit.HOURS), 20_000, List.of(UUID.randomUUID().toString()));
        when(operationsFacade.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(target, true, "ACTIVE", 20L,
                Instant.now().plus(96, ChronoUnit.HOURS), Instant.now().plus(98, ChronoUnit.HOURS), 5, LocalDate.now().plusDays(4)));
        when(loyalty.checkTicketVoucherScope(any())).thenReturn(new TicketVoucherScope(true, false, java.math.BigInteger.valueOf(20_000L)));
        when(operationsFacade.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        CurrentAccountActor buyer = new CurrentAccountActor(100L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

        ChangeResultDto preview = changeService.changeTicketSessions(ticket, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(target), null, null, null), key(), "CORR", buyer);
        assertThat(preview.applied()).isFalse();
        assertThat(preview.confirmIncreaseRequired()).isTrue();
        assertThat(balanceOwed(ticket)).isEqualTo(100_000L);

        ChangeResultDto applied = changeService.changeTicketSessions(ticket, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(target), true, null, null), key(), "CORR", buyer);
        assertThat(applied.applied()).isTrue();
        assertThat(applied.countedChange()).isTrue();
        assertThat(balanceOwed(ticket)).as("the increase goes to the checkout balance").isEqualTo(120_000L);

        ChangeResultDto queued = changeService.changeTicketSessions(ticket, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(target), true, null, null), key(), "CORR", buyer);
        assertThat(queued.applied()).isFalse();
        assertThat(queued.changeRequestReference()).isNotBlank();
        assertThat(changeRequestService.list(MANAGER, "PENDING")).anySatisfy(request ->
                assertThat(request.requestReference()).isEqualTo(queued.changeRequestReference()));

        var decision = changeRequestService.decide(queued.changeRequestReference(),
                new ChangeRequestDecisionDto(ChangeRequestDecision.APPROVE, "Customer called the shop"), key(), "CORR", MANAGER);
        assertThat(decision.status()).isEqualTo("APPROVED");
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket)).isEqualTo(2L);
        assertThat(changes.countByTicketCodeAndChangeTypeAndAppliedTrue(ticket, "MANAGER_GRANTED")).isEqualTo(1L);
    }

    @Test
    @DisplayName("group change: every ticket spends its own allowance in one 003.2 call; one blocked ticket refuses all")
    void groupChangeAllOrNone() {
        String group = unique("G-GCH-");
        String t1 = unique("T-GCH-1-");
        String t2 = unique("T-GCH-2-");
        UUID ops1 = UUID.randomUUID();
        UUID ops2 = UUID.randomUUID();
        folio(t1, group, 100_000, 50_000, "OPEN");
        folio(t2, group, 100_000, 50_000, "OPEN");
        ctx(t1, group, 100L, ops1, Instant.now().plus(72, ChronoUnit.HOURS), 0, List.of(UUID.randomUUID().toString()));
        ctx(t2, group, 100L, ops2, Instant.now().plus(72, ChronoUnit.HOURS), 0, List.of(UUID.randomUUID().toString()));
        when(operationsFacade.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        CurrentAccountActor buyer = new CurrentAccountActor(100L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        GroupTicketChangeRequestDto request = new GroupTicketChangeRequestDto(TicketChangeType.ADVANCE, List.of(
                new GroupTicketChangeItemDto(t1, List.of(UUID.randomUUID())),
                new GroupTicketChangeItemDto(t2, List.of(UUID.randomUUID()))), null, null, null);

        var result = groupChangeService.changeGroupTickets(group, request, key(), "CORR", buyer);

        assertThat(result.applied()).isTrue();
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(t1)).isEqualTo(1L);
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(t2)).isEqualTo(1L);
        verify(operationsFacade, org.mockito.Mockito.times(1)).replaceTicketSessions(any());

        assertThatThrownBy(() -> groupChangeService.changeGroupTickets(group, request, key(), "CORR", buyer))
                .hasMessageContaining("One or more tickets cannot be changed");
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(t1)).isEqualTo(1L);
    }
}
