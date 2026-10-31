package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundCommand;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundReconciliationCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RefundConsequenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundAllocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundAllocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WorkshopRefundServiceTest {
    private static final String CASE_REF = "SETTLE-CANCEL-CASE-1";
    private static final String BOOKING_REF = "BK-CANCEL-1";

    private final VnpayMerchantOperationsPort provider = mock(VnpayMerchantOperationsPort.class);
    private final SettlementRefundAllocationRepository allocations = mock(SettlementRefundAllocationRepository.class);
    private final SettlementCaseRepository cases = mock(SettlementCaseRepository.class);
    private final SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private final Map<String, SettlementRefundAllocation> allocationRows = new ConcurrentHashMap<>();
    private SettlementCase cancellationCase;
    private WorkshopRefundService service;

    @BeforeEach
    void setUp() {
        allocationRows.clear();
        cancellationCase = SettlementCase.builder().caseReference(CASE_REF).bookingReference(BOOKING_REF)
                .caseType("CUSTOMER_CANCELLATION").lifecycleStatus("PENDING_PROVIDER")
                .correlationReference("CORR-1").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        service = new WorkshopRefundService(provider, allocations, cases, audits, operations, transactions,
                "8.8.8.8", "sandbox-merchant");
        when(cases.findByCaseReference(CASE_REF)).thenReturn(Optional.of(cancellationCase));
        when(cases.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(allocations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(allocations.findByAllocationReference(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(allocationRows.get(invocation.getArgument(0))));
        when(allocations.findByAllocationReferenceForUpdate(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(allocationRows.get(invocation.getArgument(0))));
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(CASE_REF)).thenAnswer(invocation ->
                allocationRows.values().stream().sorted(java.util.Comparator.comparing(
                        SettlementRefundAllocation::getCreatedAt)).toList());
        when(operations.applyRefund(eq(BOOKING_REF), anyString(), anyString(), anyString()))
                .thenReturn(RefundConsequenceOutcome.accepted());
    }

    @Test
    void acceptedRefundIsImmediatelyQueriedAndOnlyVerifiedQueryAppliesCancellation() {
        SettlementRefundAllocation allocation = allocation("ALLOC-1", "PAY-1", 50_000, 50_000, "02");
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(CASE_REF)).thenReturn(List.of(allocation));
        when(provider.requestRefund(any(VnpayRefundCommand.class))).thenReturn(
                result("REFUND", "ACCEPTED", "00", "02", "06", "70001", "PAY-1", 50_000L, true));
        when(provider.queryRefund(any(VnpayRefundReconciliationCommand.class))).thenReturn(
                result("REFUND_QUERYDR", "VERIFIED", "00", "02", "00", "70001", "PAY-1", 50_000L, true));

        service.dispatchCase(CASE_REF);

        assertThat(allocation.getLifecycleStatus()).isEqualTo("VERIFIED");
        assertThat(cancellationCase.getLifecycleStatus()).isEqualTo("RESOLVED");
        verify(provider).requestRefund(argThat(command -> command.amountVnd() == 50_000
                && "PAY-1".equals(command.merchantReference())
                && "20261030181359".equals(command.originalTransactionDate())));
        verify(provider).queryRefund(argThat(command -> command.expectedRefundAmountVnd() == 50_000
                && "70001".equals(command.refundTransactionNumber())));
        verify(operations).applyRefund(eq(BOOKING_REF), anyString(), anyString(), eq("CORR-1"));
    }

    @Test
    void dispatchesMlandRemedyRefundThroughSameVerifiedProviderPath() {
        cancellationCase.setCaseType("MLAND_CANCELLATION_REMEDY");
        SettlementRefundAllocation allocation = allocation("ALLOC-MLAND", "PAY-MLAND", 50_000, 25_000, "03");
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(CASE_REF)).thenReturn(List.of(allocation));
        when(provider.requestRefund(any(VnpayRefundCommand.class))).thenReturn(
                result("REFUND", "ACCEPTED", "00", "03", "06", "70009", "PAY-MLAND", 25_000L, true));
        when(provider.queryRefund(any(VnpayRefundReconciliationCommand.class))).thenReturn(
                result("REFUND_QUERYDR", "VERIFIED", "00", "03", "00", "70009", "PAY-MLAND", 25_000L, true));

        service.dispatchCase(CASE_REF);

        assertThat(allocation.getLifecycleStatus()).isEqualTo("VERIFIED");
        assertThat(cancellationCase.getLifecycleStatus()).isEqualTo("RESOLVED");
        verify(provider).requestRefund(any(VnpayRefundCommand.class));
        verify(operations).applyRefund(eq(BOOKING_REF), anyString(), anyString(), eq("CORR-1"));
    }

    @Test
    void pendingQueryDoesNotApplyCancellationAndDoesNotResendRefund() {
        SettlementRefundAllocation allocation = allocation("ALLOC-1", "PAY-1", 50_000, 25_000, "03");
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(CASE_REF)).thenReturn(List.of(allocation));
        when(provider.requestRefund(any(VnpayRefundCommand.class))).thenReturn(
                result("REFUND", "ACCEPTED", "00", "03", "06", "70002", "PAY-1", 25_000L, true));
        when(provider.queryRefund(any(VnpayRefundReconciliationCommand.class))).thenReturn(
                result("REFUND_QUERYDR", "PENDING", "00", "03", "06", "70002", "PAY-1", 25_000L, true));

        service.dispatchCase(CASE_REF);
        service.dispatchCase(CASE_REF);

        assertThat(allocation.getLifecycleStatus()).isEqualTo("ACCEPTED");
        assertThat(cancellationCase.getLifecycleStatus()).isEqualTo("PENDING_PROVIDER");
        verify(provider, times(1)).requestRefund(any(VnpayRefundCommand.class));
        verify(operations, never()).applyRefund(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unknownWithoutRefundTransactionNumberStaysPendingWithoutRetry() {
        SettlementRefundAllocation allocation = allocation("ALLOC-1", "PAY-1", 50_000, 25_000, "03");
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(CASE_REF)).thenReturn(List.of(allocation));
        when(provider.requestRefund(any(VnpayRefundCommand.class))).thenReturn(
                result("REFUND", "UNKNOWN", "99", null, null, null, "PAY-1", null, false));

        service.dispatchCase(CASE_REF);
        service.dispatchCase(CASE_REF);

        assertThat(allocation.getLifecycleStatus()).isEqualTo("UNKNOWN");
        assertThat(cancellationCase.getLifecycleStatus()).isEqualTo("PENDING_PROVIDER");
        verify(provider, times(1)).requestRefund(any(VnpayRefundCommand.class));
        verifyNoMoreInteractions(provider);
        verify(operations, never()).applyRefund(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void laterRejectedAllocationAfterVerifiedRefundRemainsPendingForManualRemediation() {
        SettlementRefundAllocation oldest = allocation("ALLOC-1", "PAY-1", 40_000, 40_000, "02");
        SettlementRefundAllocation next = allocation("ALLOC-2", "PAY-2", 60_000, 10_000, "03");
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(CASE_REF)).thenReturn(List.of(oldest, next));
        when(provider.requestRefund(any(VnpayRefundCommand.class))).thenReturn(
                result("REFUND", "ACCEPTED", "00", "02", "06", "70003", "PAY-1", 40_000L, true),
                result("REFUND", "REJECTED", "09", "03", "09", "70004", "PAY-2", 10_000L, true));
        when(provider.queryRefund(any(VnpayRefundReconciliationCommand.class))).thenReturn(
                result("REFUND_QUERYDR", "VERIFIED", "00", "02", "00", "70003", "PAY-1", 40_000L, true));

        service.dispatchCase(CASE_REF);

        assertThat(oldest.getLifecycleStatus()).isEqualTo("VERIFIED");
        assertThat(next.getLifecycleStatus()).isEqualTo("REJECTED");
        assertThat(cancellationCase.getLifecycleStatus()).isEqualTo("PENDING_PROVIDER");
        assertThat(cancellationCase.getReasonReference()).isEqualTo("REFUND_PARTIAL_MANUAL_REMEDIATION_REQUIRED");
        verify(provider, times(2)).requestRefund(any(VnpayRefundCommand.class));
        verify(operations, never()).applyRefund(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void scheduledReconciliationLimitsQueriesToKnownRefundsDueAfterFiveMinutesAndWithinTwentyFourHours() {
        Instant before = Instant.now();

        service.reconcileDueRefunds();

        Instant after = Instant.now();
        var cutoffs = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(allocations).findDueRefundQueries(eq(List.of("ACCEPTED", "UNKNOWN")),
                cutoffs.capture(), cutoffs.capture());
        assertThat(cutoffs.getAllValues().get(0)).isBetween(before.minus(Duration.ofHours(24)),
                after.minus(Duration.ofHours(24)));
        assertThat(cutoffs.getAllValues().get(1)).isBetween(before.minus(Duration.ofMinutes(5)),
                after.minus(Duration.ofMinutes(5)));
        verify(provider, never()).queryRefund(any(VnpayRefundReconciliationCommand.class));
        verify(provider, never()).requestRefund(any(VnpayRefundCommand.class));
    }

    private SettlementRefundAllocation allocation(String ref, String paymentRef, long original, long amount, String type) {
        SettlementRefundAllocation allocation = SettlementRefundAllocation.builder()
                .allocationReference(ref).caseReference(CASE_REF).originalPaymentReference(paymentRef)
                .originalProviderTransactionNumber(paymentRef.equals("PAY-1") ? "10001" : "10002")
                .originalProviderCreateDate("20261030181359").originalAmountVnd(original)
                .allocatedAmountVnd(amount).currencyCode("VND").operationReference("OP-" + ref)
                .providerTransactionType(type).lifecycleStatus("PREPARED")
                .createdAt(Instant.parse(paymentRef.equals("PAY-1") ? "2026-10-30T18:00:00Z" : "2026-10-30T18:01:00Z"))
                .updatedAt(Instant.now()).build();
        allocationRows.put(ref, allocation);
        return allocation;
    }

    private static VnpayMerchantOperationResult result(String kind, String status, String code, String type,
            String transactionStatus, String txnNumber, String merchantRef, Long amount, boolean signatureVerified) {
        return new VnpayMerchantOperationResult("OP", kind, status, code, type, transactionStatus,
                txnNumber, merchantRef, amount, signatureVerified);
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
