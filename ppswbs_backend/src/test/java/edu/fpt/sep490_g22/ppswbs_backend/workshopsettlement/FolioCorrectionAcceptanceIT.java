package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundCommand;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundReconciliationCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ApplyVerifiedDepositPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLifecycleService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundAllocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCorrectionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundAllocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FinalBookingBillService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BankRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCorrectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * 003.3 T040 MySQL acceptance (FR-004c, D7-D10): a Manager overcharge correction refunds the oldest
 * verified payment first, completes only on matching QueryDR, never touches the finalized folio,
 * the booking, tickets or capacity, and two concurrent corrections can never refund the same money twice.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {
        "vnpay.merchant-api-ip-address=127.0.0.1",
        "vnpay.merchant-api-created-by=it-merchant",
        "workshop.operations.visible-location-ids="})
class FolioCorrectionAcceptanceIT {

    private static final long POST_PROMOTION = 100_001L;
    private static final long DEPOSIT = 50_001L;
    private static final long BALANCE = POST_PROMOTION - DEPOSIT;
    private static final String DEPOSIT_CREATED = "20261105100000";
    private static final String FINAL_CREATED = "20261105110000";

    @Autowired private BookingLifecycleService lifecycleService;
    @Autowired private BookingInvoiceRepository invoices;
    @Autowired private BookingPaymentAttemptRepository attempts;
    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private SettlementFinalBillRepository bills;
    @Autowired private SettlementEvidenceConclusionRepository evidence;
    @Autowired private SettlementCaseRepository cases;
    @Autowired private SettlementRefundAllocationRepository allocations;
    @Autowired private SettlementNotificationHandoffRepository handoffs;
    @Autowired private FinalBookingBillService finalBills;
    @Autowired private FolioCorrectionService corrections;
    @Autowired private BankRefundService bankRefunds;

    @MockitoSpyBean private WorkshopBookingFacade bookingFacade;
    @MockitoBean private WorkshopOperationsFacade operations;
    @MockitoBean private VnpayMerchantOperationsPort provider;

    private final CurrentAccountActor manager = new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private final CurrentAccountActor staff = new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));
    private UUID currentSession;
    /** Provider status the QueryDR mock reports for a refund: "00" verified, "05" still processing. */
    private volatile String refundQueryStatus;

    @BeforeAll
    static void refundEncryptionKey() {
        ReflectionTestUtils.invokeMethod(
                edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundAccountCipher.class,
                "useKeyForTests", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    @BeforeEach
    void setUp() {
        reset(provider);
        refundQueryStatus = "00";
        when(operations.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(
                true, "PUBLISHED", 1L, Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(3600), 10));
        when(operations.reserveBookingCapacity(any())).thenAnswer(invocation -> CapacityHoldOutcome.accepted(
                List.of(UUID.randomUUID()), Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(operations.commitBookingHolds(any())).thenReturn(CapacityCommitOutcome.accepted());
        when(operations.getBookingTicketProjection(any())).thenAnswer(invocation -> new DeliverySafeTicketSet(List.of(
                new DeliverySafeTicket(UUID.randomUUID(), "QR-FC-" + UUID.randomUUID(),
                        "TK-FC-" + UUID.randomUUID().toString().substring(0, 12),
                        List.of(new SessionEntitlementProjection(currentSession, 1L,
                                Instant.now().plus(1, ChronoUnit.DAYS),
                                Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(3600)))))));
        when(operations.getSettlementReadiness(any())).thenReturn(new SettlementReadiness(true));

        when(provider.requestRefund(any())).thenAnswer(invocation -> {
            VnpayRefundCommand command = invocation.getArgument(0);
            String type = command.amountVnd() == originalAmount(command.merchantReference()) ? "02" : "03";
            return new VnpayMerchantOperationResult(command.operationReference(), "REFUND", "ACCEPTED", "00",
                    type, "05", "770" + Math.abs(command.operationReference().hashCode() % 1_000_000),
                    command.merchantReference(), command.amountVnd(), true);
        });
        when(provider.queryRefund(any())).thenAnswer(invocation -> {
            VnpayRefundReconciliationCommand command = invocation.getArgument(0);
            SettlementRefundAllocation allocation = allocations.findAll().stream()
                    .filter(a -> command.refundTransactionNumber().equals(a.getProviderRefundTransactionNumber()))
                    .findFirst().orElseThrow();
            String type = allocation.getAllocatedAmountVnd().equals(allocation.getOriginalAmountVnd()) ? "02" : "03";
            return new VnpayMerchantOperationResult(command.operationReference(), "REFUND_QUERYDR", "ACCEPTED", "00",
                    type, refundQueryStatus, command.refundTransactionNumber(), command.merchantReference(),
                    command.expectedRefundAmountVnd(), true);
        });
    }

    @Test
    void overchargeRefundsTheOldestPaymentFirstAndLeavesFolioBookingAndCapacityUntouched() throws Exception {
        Folio folio = finalizedFolio();
        clearInvocations(bookingFacade, operations);

        FolioCorrectionDtos.Response created = corrections.create(folio.bookingCode(), "key-" + UUID.randomUUID(), null,
                new FolioCorrectionDtos.CreateRequest(60_000L, "Customer was overcharged"), manager);

        assertThat(created.lifecycleStatus()).isEqualTo("PENDING_PROVIDER");
        var caseReference = cases.findAll().stream()
                .filter(c -> folio.bookingCode().equals(c.getBookingReference()) && "FOLIO_CORRECTION".equals(c.getCaseType()))
                .findFirst().orElseThrow();
        assertThat(caseReference.getLifecycleStatus()).isEqualTo("PENDING_PROVIDER");
        List<SettlementRefundAllocation> refunded = allocations.findAllByCaseReferenceOrderByCreatedAtAsc(caseReference.getCaseReference());
        // Oldest verified payment first (the deposit), capped at its amount; the rest comes from the final payment.
        assertThat(refunded).extracting(SettlementRefundAllocation::getOriginalPaymentReference)
                .containsExactly(folio.depositReference(), folio.finalReference());
        assertThat(refunded).extracting(SettlementRefundAllocation::getAllocatedAmountVnd)
                .containsExactly(DEPOSIT, 60_000L - DEPOSIT);
        assertThat(refunded).allSatisfy(a -> assertThat(a.getLifecycleStatus()).isEqualTo("PENDING_BANK_REFUND"));
        completeBankRefunds(folio.bookingCode());
        assertThat(cases.findByCaseReference(caseReference.getCaseReference()).orElseThrow().getLifecycleStatus())
                .isEqualTo("RESOLVED");
        assertThat(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(caseReference.getCaseReference()))
                .allSatisfy(a -> assertThat(a.getLifecycleStatus()).isEqualTo("VERIFIED"));

        // T006: the resolved correction hands exactly one refund notice, to the booking's contact email, to Feature 007.
        var notices = handoffs.findAll().stream()
                .filter(h -> caseReference.getCaseReference().equals(h.getSettlementReference())).toList();
        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).getNotificationKind()).isEqualTo("REFUND_VERIFIED");
        assertThat(notices.get(0).getStoredRecipient()).isEqualTo("buyer@example.test");
        assertThat(notices.get(0).getRenderedSettlementContent()).contains("60.000").doesNotContain("http");

        // Non-mutation invariant: the finalized folio, the booking, tickets and capacity do not change.
        SettlementFinalBill bill = bills.findByBookingReference(folio.bookingCode()).orElseThrow();
        assertThat(bill.getLifecycleStatus()).isEqualTo("FINALIZED");
        assertThat(bill.getPostPromotionAmountVnd()).isEqualTo(POST_PROMOTION);
        assertThat(bill.getRemainingAmountVnd()).isEqualTo(BALANCE);
        WorkshopBooking booking = bookings.findByBookingCode(folio.bookingCode()).orElseThrow();
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
        verify(bookingFacade, never()).recordBookingRefundStatus(any());
        verify(bookingFacade, never()).recordBookingServiceOutcome(any());
        verify(operations, never()).applyVerifiedRefundConsequence(any());
    }

    @Test
    void aRefundStillProcessingKeepsTheCorrectionPendingAndBlocksAnotherOne() throws Exception {
        Folio folio = finalizedFolio();
        refundQueryStatus = "05";

        corrections.create(folio.bookingCode(), "key-" + UUID.randomUUID(), null,
                new FolioCorrectionDtos.CreateRequest(10_000L, "Overcharge"), manager);

        var pending = cases.findAll().stream()
                .filter(c -> folio.bookingCode().equals(c.getBookingReference()) && "FOLIO_CORRECTION".equals(c.getCaseType()))
                .findFirst().orElseThrow();
        assertThat(pending.getLifecycleStatus()).isEqualTo("PENDING_PROVIDER");
        // An unresolved refund (UNKNOWN to the customer) sends no notice yet.
        assertThat(handoffs.findAll().stream().filter(h -> pending.getCaseReference().equals(h.getSettlementReference()))).isEmpty();
        assertThat(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(pending.getCaseReference()))
                .extracting(SettlementRefundAllocation::getLifecycleStatus).containsExactly("PENDING_BANK_REFUND");
        assertThat(bills.findByBookingReference(folio.bookingCode()).orElseThrow().getLifecycleStatus()).isEqualTo("FINALIZED");
        assertThatThrownBy(() -> corrections.create(folio.bookingCode(), "key-" + UUID.randomUUID(), null,
                new FolioCorrectionDtos.CreateRequest(1_000L, "Second correction"), manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("FOLIO_CORRECTION_ALREADY_IN_PROGRESS");
    }

    @Test
    void replayWithTheSameKeyReturnsTheSameCorrectionAndNeverRefundsTwice() throws Exception {
        Folio folio = finalizedFolio();
        String key = "key-" + UUID.randomUUID();
        FolioCorrectionDtos.CreateRequest request = new FolioCorrectionDtos.CreateRequest(20_000L, "Overcharge");

        FolioCorrectionDtos.Response first = corrections.create(folio.bookingCode(), key, null, request, manager);
        FolioCorrectionDtos.Response replay = corrections.create(folio.bookingCode(), key, null, request, manager);

        assertThat(replay.correctionReference()).isEqualTo(first.correctionReference());
        assertThat(cases.findAll().stream().filter(c -> folio.bookingCode().equals(c.getBookingReference())
                && "FOLIO_CORRECTION".equals(c.getCaseType()))).hasSize(1);
        assertThat(handoffs.findAll().stream().filter(h -> first.correctionReference() != null
                && h.getSettlementReference().equals(cases.findAll().stream()
                        .filter(c -> folio.bookingCode().equals(c.getBookingReference())
                                && "FOLIO_CORRECTION".equals(c.getCaseType()))
                        .findFirst().orElseThrow().getCaseReference()))).isEmpty();
        assertThat(bankRefunds.listForBooking(folio.bookingCode())).hasSize(1);
        completeBankRefunds(folio.bookingCode());
        assertThat(handoffs.findAll().stream().filter(h -> h.getSettlementReference().equals(cases.findAll().stream()
                .filter(c -> folio.bookingCode().equals(c.getBookingReference())
                        && "FOLIO_CORRECTION".equals(c.getCaseType()))
                .findFirst().orElseThrow().getCaseReference()))).hasSize(1);
        verify(provider, never()).requestRefund(any());
    }

    @Test
    void staffAndOverchargeBeyondCollectionsAreRefusedWithoutAnyRefund() throws Exception {
        Folio folio = finalizedFolio();

        assertThatThrownBy(() -> corrections.create(folio.bookingCode(), "key-" + UUID.randomUUID(), null,
                new FolioCorrectionDtos.CreateRequest(1_000L, "Staff must not"), staff)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> corrections.create(folio.bookingCode(), "key-" + UUID.randomUUID(), null,
                new FolioCorrectionDtos.CreateRequest(POST_PROMOTION + 1, "Too much"), manager)).isInstanceOf(RuntimeException.class);

        verifyNoInteractions(provider);
    }

    /** Two corrections racing on one folio, with different keys, must never refund more than was collected. */
    @Test
    void twoConcurrentCorrectionsCanNeverRefundMoreThanWasCollected() throws Exception {
        Folio folio = finalizedFolio();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String key = "race-" + i + "-" + UUID.randomUUID();
                Callable<Boolean> task = () -> {
                    start.await();
                    try {
                        corrections.create(folio.bookingCode(), key, null,
                                new FolioCorrectionDtos.CreateRequest(60_000L, "Racing overcharge"), manager);
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

            assertThat(succeeded).as("only one of two 60,000 corrections fits in 100,001 collected").isEqualTo(1);
            long refundedOrReserved = allocations.findAll().stream()
                    .filter(a -> !"REJECTED".equals(a.getLifecycleStatus()))
                    .filter(a -> a.getOriginalPaymentReference().equals(folio.depositReference())
                            || a.getOriginalPaymentReference().equals(folio.finalReference()))
                    .mapToLong(SettlementRefundAllocation::getAllocatedAmountVnd).sum();
            assertThat(refundedOrReserved).isLessThanOrEqualTo(POST_PROMOTION);
            assertThat(refundedOrReserved).isEqualTo(60_000L);
        } finally {
            pool.shutdownNow();
        }
    }

    private long originalAmount(String paymentReference) {
        return allocations.findAll().stream().filter(a -> paymentReference.equals(a.getOriginalPaymentReference()))
                .map(SettlementRefundAllocation::getOriginalAmountVnd).findFirst().orElse(-1L);
    }

    private void completeBankRefunds(String bookingReference) {
        for (var refund : bankRefunds.listForBooking(bookingReference)) {
            String reference = refund.getRequestReference();
            bankRefunds.submitAccountAtCounter(reference, "BIDV", "8608150999", "Buyer",
                    staff.accountId(), "CORR-ACCOUNT-" + reference);
            bankRefunds.approve(reference, manager.accountId(), "Approved correction refund",
                    "CORR-APPROVE-" + reference);
            bankRefunds.recordTransfer(reference, manager.accountId(), "BANK-" + reference,
                    "PROOF-" + reference, "CORR-TRANSFER-" + reference);
            bankRefunds.confirmRefunded(reference, manager.accountId(), "CORR-CONFIRM-" + reference);
        }
    }

    /** Confirms a deposit, collects the balance and finalizes the folio, as the real checkout does. */
    private Folio finalizedFolio() throws InterruptedException {
        String bookingCode = "BK-FC-" + UUID.randomUUID().toString().substring(0, 8);
        currentSession = UUID.randomUUID();
        WorkshopBooking booking = lifecycleService.createPendingBookingReservation(
                bookingCode, 1L, "Test package", "buyer@example.test", "0900000000", null,
                1, List.of(currentSession), POST_PROMOTION, POST_PROMOTION,
                "OP-RES-" + bookingCode, "CORR-RES-" + bookingCode);
        BookingInvoice invoice = invoices.findByBookingId(booking.getId()).orElseThrow();
        BookingPaymentAttempt attempt = attempts.save(BookingPaymentAttempt.builder()
                .booking(booking).invoice(invoice)
                .attemptReference("ATT-" + UUID.randomUUID())
                .merchantReference("DEP-" + bookingCode + "-" + UUID.randomUUID())
                .attemptAmount(invoice.getDepositAmount())
                .status(PaymentAttemptStatus.PENDING)
                .providerCreateDate(DEPOSIT_CREATED)
                .expiresAt(booking.getHoldExpiresAt())
                .build());
        String depositTransaction = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + 1);
        assertThat(bookingFacade.applyVerifiedDepositPaymentEvidence(new ApplyVerifiedDepositPaymentEvidenceCommand(
                bookingCode, "VNPAY-" + depositTransaction, attempt.getMerchantReference(), DEPOSIT, "VND",
                "OP-" + depositTransaction, "CORR-" + bookingCode)).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        awaitFolio(bookingCode);

        String finalReference = "FIN-" + UUID.randomUUID();
        evidence.saveAndFlush(SettlementEvidenceConclusion.builder()
                .evidenceReference(finalReference).bookingReference(bookingCode)
                .providerTransactionReference(finalReference)
                .providerTransactionNumber(String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + 1))
                .originalProviderCreateDate(FINAL_CREATED)
                .conclusion("FINAL_PAYMENT_VERIFIED").amountVnd(BALANCE).currencyCode("VND")
                .operationReference("OP-" + finalReference).createdAt(Instant.now()).build());
        assertThat(finalBills.finalizeAuthorized(bookingCode, 0L, "OP-FIN-" + bookingCode, "CORR-FIN").getLifecycleStatus())
                .isEqualTo("FINALIZED");
        return new Folio(bookingCode, attempt.getMerchantReference(), finalReference);
    }

    /** The confirmation event reaches the folio listener asynchronously after commit. */
    private void awaitFolio(String bookingCode) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Optional<SettlementFinalBill> folio = bills.findByBookingReference(bookingCode);
            if (folio.isPresent()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("No folio opened for " + bookingCode);
    }

    private record Folio(String bookingCode, String depositReference, String finalReference) { }
}
