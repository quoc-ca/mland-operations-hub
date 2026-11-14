package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundCommand;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundReconciliationCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRefundStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RefundConsequenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundAllocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundAllocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementCustomerNotifier;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopCancellationLoyaltyService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class WorkshopRefundServiceTest {
    private static final String CASE_REF = "SETTLE-CANCEL-CASE-1";
    private static final String BOOKING_REF = "BK-CANCEL-1";

    private final VnpayMerchantOperationsPort provider = mock(VnpayMerchantOperationsPort.class);
    private final SettlementRefundAllocationRepository allocations = mock(SettlementRefundAllocationRepository.class);
    private final SettlementCaseRepository cases = mock(SettlementCaseRepository.class);
    private final SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final WorkshopCancellationLoyaltyService cancellationLoyalty = mock(WorkshopCancellationLoyaltyService.class);
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final SettlementCustomerNotifier notifier = mock(SettlementCustomerNotifier.class);
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BankRefundService bankRefunds =
            mock(edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BankRefundService.class);
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
        service = new WorkshopRefundService(provider, allocations, cases, audits, operations, cancellationLoyalty,
                booking, bills, notifier, bankRefunds, transactions, "8.8.8.8", "sandbox-merchant");
        when(cases.findByCaseReference(CASE_REF)).thenReturn(Optional.of(cancellationCase));
        when(cases.findByCaseReferenceForUpdate(CASE_REF)).thenReturn(Optional.of(cancellationCase));
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
    void aCustomerSelfCancellationIsRefusedAsNonRefundableAndNeverReachesTheGateway() {
        // FR-038/FR-041 (T099): a customer self-cancellation never refunds the booking-time collection, so the
        // allocation is rejected and VNPay is not asked for anything. The shared fixture is this case type.
        SettlementRefundAllocation allocation = allocation("ALLOC-1", "PAY-1", 50_000, 50_000, "02");
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(CASE_REF)).thenReturn(List.of(allocation));

        service.dispatchCase(CASE_REF);

        assertThat(cancellationCase.getLifecycleStatus()).isEqualTo("REJECTED");
        assertThat(cancellationCase.getReasonReference()).isEqualTo("CUSTOMER_CANCELLATION_NON_REFUNDABLE");
        verify(provider, never()).requestRefund(any(VnpayRefundCommand.class));
        verify(provider, never()).queryRefund(any(VnpayRefundReconciliationCommand.class));
        verify(operations, never()).applyRefund(anyString(), anyString(), anyString(), anyString());
    }

    // Removed 2026-11-14 with T099: acceptedRefundIsImmediatelyQueriedAndOnlyVerifiedQueryAppliesCancellation,
    // pendingQueryDoesNotApplyCancellationAndDoesNotResendRefund,
    // unknownWithoutRefundTransactionNumberStaysPendingWithoutRetry and
    // laterRejectedAllocationAfterVerifiedRefundRemainsPendingForManualRemediation exercised the VNPay refund
    // dispatch through a CUSTOMER_CANCELLATION case. Every case type is now either bank-routed (T098/T104:
    // MLAND_CANCELLATION_REMEDY, FOLIO_CORRECTION, LATE_PAYMENT_REFUND, ADDON_REFUND) or refused above, so no
    // case can reach provider.requestRefund any more and those tests asserted an unreachable path. The dispatch
    // code itself is left in place: removing a money flow needs the owner's decision (003.3 tasks, T099 note).

    @Test
    void aMlandRemedyGoesBackByBankTransferAndNeverReachesTheGateway() {
        cancellationCase.setCaseType("MLAND_CANCELLATION_REMEDY");
        SettlementRefundAllocation allocation = allocation("ALLOC-MLAND", "PAY-MLAND", 50_000, 25_000, "03");
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(CASE_REF)).thenReturn(List.of(allocation));
        givenBankRefundOpens("REFUND-1");

        service.dispatchCase(CASE_REF);

        // T098: Mland's own money is refunded outside the system, so VNPay is never asked for a refund.
        verify(provider, never()).requestRefund(any(VnpayRefundCommand.class));
        assertThat(allocation.getLifecycleStatus()).isEqualTo("PENDING_BANK_REFUND");
        assertThat(allocation.getBankRefundReference()).isEqualTo("REFUND-1");
        // The original payment evidence stays: it is still what proves Mland holds the money.
        assertThat(allocation.getOriginalPaymentReference()).isEqualTo("PAY-MLAND");
        // Nothing is settled before a Manager confirms the transfer, so no consequence has run.
        assertThat(cancellationCase.getLifecycleStatus()).isNotEqualTo("RESOLVED");
        verify(cancellationLoyalty, never()).restoreVerifiedCancellation(any(), anyString());
        verify(booking).recordBookingRefundStatus(eq(BOOKING_REF), eq(BookingRefundStatus.REFUND_PENDING),
                eq(CASE_REF), anyString(), eq("CORR-1"));
        verify(booking, never()).recordBookingRefundStatus(eq(BOOKING_REF), eq(BookingRefundStatus.REFUNDED),
                eq(CASE_REF), anyString(), eq("CORR-1"));
    }

    @Test
    void theManagersBankConfirmationSettlesTheAllocationAndItsConsequences() {
        cancellationCase.setCaseType("MLAND_CANCELLATION_REMEDY");
        SettlementRefundAllocation allocation = allocation("ALLOC-MLAND", "PAY-MLAND", 50_000, 25_000, "03");
        allocation.setLifecycleStatus("PENDING_BANK_REFUND");
        allocation.setBankRefundReference("REFUND-1");
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(CASE_REF)).thenReturn(List.of(allocation));
        SettlementFinalBill openFolio = SettlementFinalBill.builder().billReference("BILL-MLAND")
                .bookingReference(BOOKING_REF).lifecycleStatus("OPEN").build();
        when(bills.findByBookingReference(BOOKING_REF)).thenReturn(Optional.of(openFolio));

        service.onBankRefundConfirmed(
                new edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBankRefundConfirmedEvent(
                        "REFUND-1", BOOKING_REF, "ALLOC-MLAND", 25_000, Instant.now()));

        assertThat(allocation.getLifecycleStatus()).isEqualTo("VERIFIED");
        assertThat(cancellationCase.getLifecycleStatus()).isEqualTo("RESOLVED");
        verify(cancellationLoyalty).restoreVerifiedCancellation(eq(cancellationCase), anyString());
        verify(booking).recordBookingRefundStatus(eq(BOOKING_REF), eq(BookingRefundStatus.REFUNDED), eq(CASE_REF),
                anyString(), eq("CORR-1"));
        assertThat(openFolio.getLifecycleStatus()).isEqualTo("CLOSED");
    }

    @Test
    void anUnreconciledLegacyGatewayRefundIsLeftAloneInsteadOfPaidTwice() {
        cancellationCase.setCaseType("MLAND_CANCELLATION_REMEDY");
        SettlementRefundAllocation allocation = allocation("ALLOC-MLAND", "PAY-MLAND", 50_000, 25_000, "03");
        allocation.setProviderResponseCode("99");
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(CASE_REF)).thenReturn(List.of(allocation));

        service.dispatchCase(CASE_REF);

        assertThat(allocation.getLifecycleStatus()).isEqualTo("PREPARED");
        assertThat(allocation.getBankRefundReference()).isNull();
        verify(provider, never()).requestRefund(any(VnpayRefundCommand.class));
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

    @Test
    void folioCorrectionCaseDoesNotMutateFolioOrBookingOrConsequencesWhenFullyRefunded() {
        String corrCaseRef = "CASE-CORR-1";
        SettlementCase corrCase = SettlementCase.builder().caseReference(corrCaseRef).bookingReference(BOOKING_REF)
                .caseType("FOLIO_CORRECTION").lifecycleStatus("PENDING_PROVIDER")
                .correlationReference("CORR-CORR-1").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(cases.findByCaseReference(corrCaseRef)).thenReturn(Optional.of(corrCase));
        when(cases.findByCaseReferenceForUpdate(corrCaseRef)).thenReturn(Optional.of(corrCase));

        SettlementRefundAllocation alloc = allocation("ALLOC-CORR", "PAY-1", 40_000, 20_000, "03");
        alloc.setCaseReference(corrCaseRef);
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(corrCaseRef)).thenReturn(List.of(alloc));
        givenBankRefundOpens("REFUND-CORR");

        service.dispatchCase(corrCaseRef);

        // T098: an overcharge corrected after FINALIZED is Mland money, so it leaves by bank transfer too.
        verify(provider, never()).requestRefund(any(VnpayRefundCommand.class));
        assertThat(alloc.getLifecycleStatus()).isEqualTo("PENDING_BANK_REFUND");

        // The correction resolves only once the Manager has confirmed the bank transfer.
        assertThat(corrCase.getLifecycleStatus()).isNotEqualTo("RESOLVED");

        service.onBankRefundConfirmed(
                new edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBankRefundConfirmedEvent(
                        "REFUND-CORR", BOOKING_REF, "ALLOC-CORR", 20_000, Instant.now()));

        assertThat(alloc.getLifecycleStatus()).isEqualTo("VERIFIED");
        assertThat(corrCase.getLifecycleStatus()).isEqualTo("RESOLVED");

        // A folio correction still touches no booking, folio or cancellation consequence.
        verify(operations, never()).applyRefund(anyString(), anyString(), anyString(), anyString());
        verify(cancellationLoyalty, never()).restoreVerifiedCancellation(any(), anyString());
        verify(booking, never()).recordBookingRefundStatus(anyString(), any(), anyString(), anyString(), anyString());
        verify(booking, never()).recordBookingServiceOutcome(anyString(), any(), anyString(), anyString(), anyString());
        verify(bills, never()).saveAndFlush(any());
        verify(bills, never()).findByBookingReference(anyString());

        var auditCaptor = org.mockito.ArgumentCaptor.forClass(SettlementBusinessAudit.class);
        verify(audits, atLeastOnce()).save(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues()).anyMatch(a -> "FOLIO_CORRECTION_REFUND_VERIFIED".equals(a.getActionCode()));
    }

    @Test
    void addonRefundCaseDoesNotMutateFolioOrBookingOrConsequencesWhenFullyRefunded() {
        String addonCaseRef = "CASE-ADDON-1";
        SettlementCase addonCase = SettlementCase.builder().caseReference(addonCaseRef).bookingReference("ext-booking-ref-42")
                .caseType("ADDON_REFUND").lifecycleStatus("PENDING_PROVIDER")
                .correlationReference("CORR-ADDON-1").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(cases.findByCaseReference(addonCaseRef)).thenReturn(Optional.of(addonCase));
        when(cases.findByCaseReferenceForUpdate(addonCaseRef)).thenReturn(Optional.of(addonCase));

        SettlementRefundAllocation alloc = allocation("ALLOC-ADDON", "PAY-ADDON-1", 50_000, 50_000, "02");
        alloc.setCaseReference(addonCaseRef);
        when(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(addonCaseRef)).thenReturn(List.of(alloc));
        givenBankRefundOpens("REFUND-ADDON");

        service.dispatchCase(addonCaseRef);

        // T104: the add-on overcharge of an OTA booking is Mland money, so it leaves by bank transfer too.
        // The OTA's own base ticket stays the OTA's process and was never handled here.
        verify(provider, never()).requestRefund(any(VnpayRefundCommand.class));
        assertThat(alloc.getLifecycleStatus()).isEqualTo("PENDING_BANK_REFUND");
        assertThat(alloc.getBankRefundReference()).isEqualTo("REFUND-ADDON");
        assertThat(addonCase.getLifecycleStatus()).isNotEqualTo("RESOLVED");

        service.onBankRefundConfirmed(
                new edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBankRefundConfirmedEvent(
                        "REFUND-ADDON", BOOKING_REF, "ALLOC-ADDON", 50_000, Instant.now()));

        assertThat(alloc.getLifecycleStatus()).isEqualTo("VERIFIED");
        assertThat(addonCase.getLifecycleStatus()).isEqualTo("RESOLVED");

        // Verify consequences were NEVER invoked
        verify(operations, never()).applyRefund(anyString(), anyString(), anyString(), anyString());
        verify(cancellationLoyalty, never()).restoreVerifiedCancellation(any(), anyString());
        verify(booking, never()).recordBookingRefundStatus(anyString(), any(), anyString(), anyString(), anyString());
        verify(booking, never()).recordBookingServiceOutcome(anyString(), any(), anyString(), anyString(), anyString());
        verify(bills, never()).saveAndFlush(any());
        verify(bills, never()).findByBookingReference(anyString());

        var auditCaptor = org.mockito.ArgumentCaptor.forClass(SettlementBusinessAudit.class);
        verify(audits, atLeastOnce()).save(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues()).anyMatch(a -> "ADDON_REFUND_VERIFIED".equals(a.getActionCode()));
    }

    /** The bank refund T097 opens when an allocation is routed away from the gateway. */
    private void givenBankRefundOpens(String requestReference) {
        var request = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest
                .builder().requestReference(requestReference).build();
        when(bankRefunds.open(anyString(), any(), any(), anyString(), anyLong(), anyString()))
                .thenReturn(new edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service
                        .BankRefundService.OpenedRefund(request, "token"));
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
