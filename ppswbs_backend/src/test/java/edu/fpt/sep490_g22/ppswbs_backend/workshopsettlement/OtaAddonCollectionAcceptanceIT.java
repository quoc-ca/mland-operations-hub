package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundCommand;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundReconciliationCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementAddonBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementAddonPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundAllocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.OtaAddonPaymentDtos.OtaAddonPaymentAttemptResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.OtaAddonPaymentDtos.OtaAddonRefundCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.OtaAddonPaymentDtos.OtaAddonRefundProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAddonPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundAllocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.OtaAddonPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MySQL acceptance for collecting and refunding an OTA booking's separate Mland shop add-on bill
 * (SHOP_ADDON, D12): payment attempt, verified evidence, PAID, partial refund, and the guarantees that
 * no folio, Booking, Operations or Loyalty fact is ever touched.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {
        "vnpay.merchant-api-ip-address=127.0.0.1",
        "vnpay.merchant-api-created-by=it-merchant",
        "workshop.operations.visible-location-ids="})
class OtaAddonCollectionAcceptanceIT {

    private static final long LOCATION = 777_001L;
    private static final long AMOUNT = 30_000L;

    @Autowired private edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository handoffs;
    @Autowired private WorkshopSettlementFacade settlement;
    @Autowired private OtaAddonPaymentService addonPayments;
    @Autowired private SettlementAddonBillRepository addonBills;
    @Autowired private SettlementAddonPaymentAttemptRepository attempts;
    @Autowired private SettlementEvidenceConclusionRepository evidence;
    @Autowired private SettlementCaseRepository cases;
    @Autowired private SettlementRefundAllocationRepository allocations;
    @Autowired private SettlementFinalBillRepository folios;

    @MockitoBean private PaymentGatewayCheckoutPort checkout;
    @MockitoBean private VnpayMerchantOperationsPort provider;
    @MockitoBean private WorkshopOperationsFacade operations;
    @MockitoBean private LoyaltyFacade loyalty;
    @MockitoSpyBean private WorkshopBookingFacade bookingFacade;

    private final CurrentAccountActor manager = new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private final CurrentAccountActor staffInScope = new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(LOCATION));
    private final CurrentAccountActor staffOutOfScope = new CurrentAccountActor(21L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(LOCATION + 1));
    private volatile String refundQueryStatus;

    @BeforeEach
    void setUp() {
        refundQueryStatus = "00";
        when(checkout.createShopAddonUrl(any(), any(), anyLong(), any(), any(), any()))
                .thenAnswer(invocation -> "https://pay.example.invalid/" + invocation.getArgument(0));
        when(provider.requestRefund(any())).thenAnswer(invocation -> {
            VnpayRefundCommand command = invocation.getArgument(0);
            String type = command.amountVnd() == paidAmount(command.merchantReference()) ? "02" : "03";
            return new VnpayMerchantOperationResult(command.operationReference(), "REFUND", "ACCEPTED", "00", type, "05",
                    "880" + Math.abs(command.operationReference().hashCode() % 1_000_000),
                    command.merchantReference(), command.amountVnd(), true);
        });
        when(provider.queryRefund(any())).thenAnswer(invocation -> {
            VnpayRefundReconciliationCommand command = invocation.getArgument(0);
            SettlementRefundAllocation allocation = allocations.findAll().stream()
                    .filter(a -> command.refundTransactionNumber().equals(a.getProviderRefundTransactionNumber()))
                    .findFirst().orElseThrow();
            String type = allocation.getAllocatedAmountVnd().equals(allocation.getOriginalAmountVnd()) ? "02" : "03";
            return new VnpayMerchantOperationResult(command.operationReference(), "REFUND_QUERYDR", "ACCEPTED", "00", type,
                    refundQueryStatus, command.refundTransactionNumber(), command.merchantReference(),
                    command.expectedRefundAmountVnd(), true);
        });
    }

    @Test
    void collectionMarksTheBillPaidOnceAndNeverTouchesFolioBookingOperationsOrLoyalty() {
        Bill bill = openBill();
        OtaAddonPaymentAttemptResponse attempt = addonPayments.startPaymentAttempt(
                bill.reference(), key(), null, "127.0.0.1", staffInScope);

        assertThat(attempt.paymentUrl()).startsWith("https://pay.example.invalid/");
        assertThat(attempt.status()).isEqualTo("PENDING");
        assertThat(attempt.amountVnd()).isEqualTo(AMOUNT);

        String transaction = uniqueTransaction();
        applyEvidence(bill, attempt, transaction);
        applyEvidence(bill, attempt, transaction); // the same IPN delivered twice

        assertThat(addonBills.findByBillReference(bill.reference()).orElseThrow().getLifecycleStatus()).isEqualTo("PAID");
        assertThat(attempts.findByAttemptReference(attempt.attemptReference()).orElseThrow().getLifecycleStatus())
                .isEqualTo("VERIFIED_PAID");
        assertThat(evidence.findByBookingReferenceAndConclusion(bill.channelReference(), "SHOP_ADDON_VERIFIED")).hasSize(1);
        assertThat(folios.findByBookingReference(bill.channelReference())).isEmpty();
        // T006 scope: the OTA add-on bill sends no Settlement notification.
        assertThat(handoffs.findAll().stream().filter(h -> bill.reference().equals(h.getSettlementReference())
                || bill.channelReference().equals(h.getSettlementReference()))).isEmpty();
        verify(bookingFacade, never()).recordBookingServiceOutcome(any());
        verify(bookingFacade, never()).recordBookingRefundStatus(any());
        verify(bookingFacade, never()).recordFinalBalanceResolved(any());
        verify(operations, never()).applyVerifiedRefundConsequence(any());
        // Scoped to this bill: the shared test DB may hold other bookings the Loyalty scheduler legitimately processes.
        verify(loyalty, never()).findBenefitResult(any(), org.mockito.ArgumentMatchers.eq(bill.channelReference()));
    }

    @Test
    void onlyInScopeStaffOrAManagerMayStartPaymentAndAPendingAttemptIsReused() {
        Bill bill = openBill();

        assertThatThrownBy(() -> addonPayments.startPaymentAttempt(bill.reference(), key(), null, "127.0.0.1", staffOutOfScope))
                .isInstanceOf(RuntimeException.class);

        OtaAddonPaymentAttemptResponse first = addonPayments.startPaymentAttempt(bill.reference(), key(), null, "127.0.0.1", staffInScope);
        OtaAddonPaymentAttemptResponse second = addonPayments.startPaymentAttempt(bill.reference(), key(), null, "127.0.0.1", manager);

        assertThat(second.attemptReference()).isEqualTo(first.attemptReference());
        verify(checkout, times(1)).createShopAddonUrl(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void managerRefundsPartOfAPaidAddonAndTheBillStaysPaid() {
        Bill bill = paidBill();

        OtaAddonRefundProjection refund = addonPayments.createRefund(bill.reference(), key(), null,
                new OtaAddonRefundCommand(12_000L, "Customer returned the item"), manager);

        assertThat(refund.amountVnd()).isEqualTo(12_000L);
        var refundCase = cases.findByCaseReference(refund.caseReference()).orElseThrow();
        assertThat(refundCase.getCaseType()).isEqualTo("ADDON_REFUND");
        assertThat(refundCase.getLifecycleStatus()).isEqualTo("RESOLVED");
        assertThat(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(refund.caseReference()))
                .singleElement().satisfies(a -> {
                    assertThat(a.getAllocatedAmountVnd()).isEqualTo(12_000L);
                    assertThat(a.getLifecycleStatus()).isEqualTo("VERIFIED");
                });
        assertThat(addonBills.findByBillReference(bill.reference()).orElseThrow().getLifecycleStatus()).isEqualTo("PAID");
        assertThat(folios.findByBookingReference(bill.channelReference())).isEmpty();
        verify(bookingFacade, never()).recordBookingRefundStatus(any());
        verify(bookingFacade, never()).recordBookingServiceOutcome(any());
        verify(operations, never()).applyVerifiedRefundConsequence(any());
        // Scoped to this bill: the shared test DB may hold other bookings the Loyalty scheduler legitimately processes.
        verify(loyalty, never()).findBenefitResult(any(), org.mockito.ArgumentMatchers.eq(bill.channelReference()));
    }

    @Test
    void staffAndAnOverRefundAreRefusedWithoutAnyProviderCall() {
        Bill bill = paidBill();

        assertThatThrownBy(() -> addonPayments.createRefund(bill.reference(), key(), null,
                new OtaAddonRefundCommand(1_000L, "Staff must not"), staffInScope)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> addonPayments.createRefund(bill.reference(), key(), null,
                new OtaAddonRefundCommand(AMOUNT + 1, "Too much"), manager)).isInstanceOf(RuntimeException.class);

        verify(provider, never()).requestRefund(any());
    }

    /** Idempotency: the same key must return the original refund even while it is still waiting for QueryDR. */
    @Test
    void retryingTheSameRefundKeyWhileItIsPendingReturnsTheOriginalRefund() {
        Bill bill = paidBill();
        refundQueryStatus = "05";
        String key = key();
        OtaAddonRefundCommand command = new OtaAddonRefundCommand(10_000L, "Customer returned the item");

        OtaAddonRefundProjection first = addonPayments.createRefund(bill.reference(), key, null, command, manager);
        OtaAddonRefundProjection retry = addonPayments.createRefund(bill.reference(), key, null, command, manager);

        assertThat(retry.refundReference()).isEqualTo(first.refundReference());
        verify(provider, times(1)).requestRefund(any());
    }

    /** A verified payment that arrives for an already PAID bill is real money and must be recorded to be refundable. */
    @Test
    void aSecondVerifiedPaymentForAnAlreadyPaidBillIsRecordedSoItCanBeRefunded() {
        Bill bill = openBill();
        OtaAddonPaymentAttemptResponse first = addonPayments.startPaymentAttempt(bill.reference(), key(), null, "127.0.0.1", staffInScope);
        SettlementAddonPaymentAttempt expired = attempts.findByAttemptReference(first.attemptReference()).orElseThrow();
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        attempts.saveAndFlush(expired);
        OtaAddonPaymentAttemptResponse second = addonPayments.startPaymentAttempt(bill.reference(), key(), null, "127.0.0.1", staffInScope);
        assertThat(second.attemptReference()).isNotEqualTo(first.attemptReference());

        applyEvidence(bill, first, uniqueTransaction());  // the customer paid through the first (late) URL
        applyEvidence(bill, second, uniqueTransaction()); // and also through the second URL

        assertThat(evidence.findByBookingReferenceAndConclusion(bill.channelReference(), "SHOP_ADDON_VERIFIED"))
                .as("both verified payments are on record").hasSize(2);
    }

    @Test
    void twoConcurrentRefundsCanNeverRefundMoreThanWasPaid() throws Exception {
        Bill bill = paidBill();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String key = key();
                Callable<Boolean> task = () -> {
                    start.await();
                    try {
                        addonPayments.createRefund(bill.reference(), key, null,
                                new OtaAddonRefundCommand(20_000L, "Racing refund"), manager);
                        return true;
                    } catch (RuntimeException refused) {
                        return false;
                    }
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> result : results) if (result.get()) succeeded++;

            assertThat(succeeded).as("only one 20,000 refund fits in a 30,000 payment").isEqualTo(1);
            long refunded = allocations.findAll().stream()
                    .filter(a -> !"REJECTED".equals(a.getLifecycleStatus()))
                    .filter(a -> a.getOriginalPaymentReference().equals(bill.paidMerchant()))
                    .mapToLong(SettlementRefundAllocation::getAllocatedAmountVnd).sum();
            assertThat(refunded).isEqualTo(20_000L);
        } finally {
            pool.shutdownNow();
        }
    }

    private long paidAmount(String merchantReference) {
        return attempts.findAll().stream().filter(a -> merchantReference.equals(a.getMerchantReference()))
                .map(SettlementAddonPaymentAttempt::getAmountVnd).findFirst().orElse(-1L);
    }

    private Bill openBill() {
        String channelReference = "CH-" + UUID.randomUUID();
        var outcome = settlement.createDirectShopAddonBill(new WorkshopSettlementFacade.DirectShopAddonBillCommand(
                channelReference, "QUOTE-" + UUID.randomUUID(), "ADDON-ITEM", "Add-on item", AMOUNT, "VND",
                "CONSENT-" + UUID.randomUUID(), "STAFF-20", LOCATION));
        assertThat(outcome.created()).isTrue();
        return new Bill(outcome.billReference(), channelReference, null);
    }

    private Bill paidBill() {
        Bill open = openBill();
        OtaAddonPaymentAttemptResponse attempt = addonPayments.startPaymentAttempt(open.reference(), key(), null, "127.0.0.1", staffInScope);
        applyEvidence(open, attempt, uniqueTransaction());
        assertThat(addonBills.findByBillReference(open.reference()).orElseThrow().getLifecycleStatus()).isEqualTo("PAID");
        return new Bill(open.reference(), open.channelReference(), attempt.merchantReference());
    }

    private void applyEvidence(Bill bill, OtaAddonPaymentAttemptResponse attempt, String transactionNumber) {
        String createDate = attempts.findByAttemptReference(attempt.attemptReference()).orElseThrow().getProviderCreateDate();
        settlement.applyVerifiedSettlementPaymentEvidence(new ApplyVerifiedSettlementPaymentEvidenceCommand(
                bill.channelReference(), "VNPAY-" + transactionNumber, attempt.merchantReference(), "SHOP_ADDON",
                AMOUNT, "VND", transactionNumber, createDate, "OP-" + transactionNumber, "CORR-" + transactionNumber));
    }

    /** Provider transaction numbers are unique per run: evidence rows persist in the shared MySQL test schema. */
    private static String uniqueTransaction() {
        return String.valueOf(1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L));
    }

    private static String key() {
        return "key-" + UUID.randomUUID();
    }

    private record Bill(String reference, String channelReference, String paidMerchant) { }
}
