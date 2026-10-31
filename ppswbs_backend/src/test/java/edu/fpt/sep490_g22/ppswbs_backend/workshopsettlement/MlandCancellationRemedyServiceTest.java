package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingDepositPaymentsProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.MlandRemedyCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MlandCancellationRemedyServiceTest {
    private SettlementCaseRepository cases;
    private SettlementCancellationCaseRepository cancellationCases;
    private SettlementCustomerCancellationSnapshotRepository customerSnapshots;
    private SettlementBookingMlandRefundPolicySnapshotRepository policySnapshots;
    private SettlementMlandRemedySnapshotRepository remedySnapshots;
    private SettlementRefundAllocationRepository allocations;
    private SettlementEvidenceConclusionRepository evidence;
    private SettlementBusinessAuditRepository audits;
    private WorkshopBookingSettlementAdapter booking;
    private WorkshopSettlementAuthorizationService authorization;
    private SettlementIdempotencyService idempotency;
    private WorkshopRefundService refunds;
    private MlandCancellationRemedyService service;
    private CurrentAccountActor manager;

    @BeforeEach
    void setUp() {
        cases = mock(SettlementCaseRepository.class);
        cancellationCases = mock(SettlementCancellationCaseRepository.class);
        customerSnapshots = mock(SettlementCustomerCancellationSnapshotRepository.class);
        policySnapshots = mock(SettlementBookingMlandRefundPolicySnapshotRepository.class);
        remedySnapshots = mock(SettlementMlandRemedySnapshotRepository.class);
        allocations = mock(SettlementRefundAllocationRepository.class);
        evidence = mock(SettlementEvidenceConclusionRepository.class);
        audits = mock(SettlementBusinessAuditRepository.class);
        booking = mock(WorkshopBookingSettlementAdapter.class);
        authorization = mock(WorkshopSettlementAuthorizationService.class);
        idempotency = mock(SettlementIdempotencyService.class);
        refunds = mock(WorkshopRefundService.class);
        service = new MlandCancellationRemedyService(cases, cancellationCases, customerSnapshots,
                policySnapshots, remedySnapshots, allocations, evidence, audits, booking,
                authorization, idempotency, refunds, new ObjectMapper());
        manager = new CurrentAccountActor(7L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
        when(authorization.requireManager(manager)).thenReturn(manager);
        when(booking.confirmedProjection("BK-M1")).thenReturn(new ConfirmedBookingFinancialProjection(
                "17", 100_001L, 100_001L, 50_001L, 50_000L, 1, List.of("SESSION"), 2L));
        when(cases.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(cases.findByCaseReference(anyString())).thenAnswer(call -> Optional.of(SettlementCase.builder()
                .caseReference(call.getArgument(0)).bookingReference("BK-M1")
                .caseType("MLAND_CANCELLATION_REMEDY").lifecycleStatus("PENDING_PROVIDER")
                .correlationReference("CORR-1").build()));
        when(remedySnapshots.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(allocations.save(any())).thenAnswer(call -> call.getArgument(0));
        when(idempotency.execute(anyString(), anyString(), any())).thenAnswer(call -> {
            @SuppressWarnings("unchecked") Supplier<String> create = call.getArgument(2);
            return new SettlementIdempotencyResult(false, create.get());
        });
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void rejectsHandoffThatDoesNotNameTheBooking() {
        when(cancellationCases.findByCaseReference("HANDOFF-1")).thenReturn(Optional.of(
                SettlementCancellationCase.builder().caseReference("HANDOFF-1")
                        .affectedBookingReferencesJson("[\"BK-OTHER\"]").build()));

        assertThatThrownBy(() -> service.open("BK-M1", "key-1", "CORR-1",
                new MlandRemedyCommandDto("session cancelled", "DECLINED", "HANDOFF-1"), manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("CANCELLATION_HANDOFF_BOOKING_MISMATCH");
        verify(allocations, never()).save(any());
    }

    @Test
    void customerSafeReadRequiresExactlyOneGuestContactProof() {
        assertThatThrownBy(() -> service.read("BK-M1", "CASE-1", "guest@example.test",
                "+84900000000", null))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
        verify(booking, never()).verifyCustomerAccess(anyString(), any(), any(), any());
    }

    @Test
    void customerSafeReadAcceptsVerifiedGuestAndReturnsOnlySafeCaseProjection() {
        when(booking.verifyCustomerAccess("BK-M1", null, "guest@example.test", null))
                .thenReturn(new CustomerBookingAccessDecision(true));

        var result = service.read("BK-M1", "CASE-1", "guest@example.test", null, null);

        assertThat(result.caseReference()).isEqualTo("CASE-1");
        assertThat(result.caseType()).isEqualTo("MLAND_CANCELLATION_REMEDY");
        assertThat(result.bookingCode()).isEqualTo("BK-M1");
        verify(booking).verifyCustomerAccess("BK-M1", null, "guest@example.test", null);
    }

    @Test
    void computesAggregateRefundThenAllocatesOldestPaymentFirst() {
        Instant confirmedAt = Instant.parse("2026-10-31T10:00:00Z");
        when(cancellationCases.findByCaseReference("HANDOFF-1")).thenReturn(Optional.of(
                SettlementCancellationCase.builder().caseReference("HANDOFF-1")
                        .affectedBookingReferencesJson("[\"BK-M1\"]").build()));
        when(customerSnapshots.findByBookingReference("BK-M1")).thenReturn(Optional.empty());
        when(remedySnapshots.findByBookingReference("BK-M1")).thenReturn(Optional.empty());
        when(policySnapshots.findByBookingReferenceForUpdate("BK-M1")).thenReturn(Optional.of(
                SettlementBookingMlandRefundPolicySnapshot.builder().snapshotReference("POLICY-SNAPSHOT")
                        .bookingReference("BK-M1").policyReference("POLICY-50").refundPercent((short) 50)
                        .confirmedAt(confirmedAt).build()));
        when(booking.verifiedDepositPayments("BK-M1")).thenReturn(new VerifiedBookingDepositPaymentsProjection(List.of(
                new VerifiedBookingPayment("PAY-NEW", "1002", "20261031110000", 60_001L, "VND", true),
                new VerifiedBookingPayment("PAY-OLD", "1001", "20261031100000", 40_000L, "VND", true))));
        when(evidence.findByBookingReferenceAndConclusion("BK-M1", "FINAL_PAYMENT_VERIFIED"))
                .thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();

        var result = service.open("BK-M1", "key-1", "CORR-1",
                new MlandRemedyCommandDto("session cancelled", "UNAVAILABLE", "HANDOFF-1"), manager);

        assertThat(result.caseType()).isEqualTo("MLAND_CANCELLATION_REMEDY");
        ArgumentCaptor<SettlementMlandRemedySnapshot> snapshot = ArgumentCaptor.forClass(SettlementMlandRemedySnapshot.class);
        verify(remedySnapshots).saveAndFlush(snapshot.capture());
        assertThat(snapshot.getValue().getCollectedAmountVnd()).isEqualTo(100_001L);
        assertThat(snapshot.getValue().getRefundableAmountVnd()).isEqualTo(50_000L);
        ArgumentCaptor<SettlementRefundAllocation> allocation = ArgumentCaptor.forClass(SettlementRefundAllocation.class);
        verify(allocations, times(2)).save(allocation.capture());
        assertThat(allocation.getAllValues()).extracting(SettlementRefundAllocation::getOriginalPaymentReference)
                .containsExactly("PAY-OLD", "PAY-NEW");
        assertThat(allocation.getAllValues()).extracting(SettlementRefundAllocation::getAllocatedAmountVnd)
                .containsExactly(40_000L, 10_000L);
    }
}
