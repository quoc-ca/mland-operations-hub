package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingDepositPaymentsProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingCancellationEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCustomerCancellationSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundAllocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.GuestContactProofDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundAllocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.CustomerBookingCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementIdempotencyResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerBookingCancellationServiceTest {
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final SettlementIdempotencyService idempotency = mock(SettlementIdempotencyService.class);
    private final SettlementCaseRepository cases = mock(SettlementCaseRepository.class);
    private final SettlementCustomerCancellationSnapshotRepository snapshots = mock(SettlementCustomerCancellationSnapshotRepository.class);
    private final SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
    private final SettlementRefundAllocationRepository allocations = mock(SettlementRefundAllocationRepository.class);
    private final WorkshopRefundService refunds = mock(WorkshopRefundService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final AtomicReference<SettlementCase> savedCase = new AtomicReference<>();
    private final List<SettlementRefundAllocation> savedAllocations = new ArrayList<>();
    private CustomerBookingCancellationService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(cases.saveAndFlush(any(SettlementCase.class))).thenAnswer(invocation -> {
            SettlementCase value = invocation.getArgument(0);
            savedCase.set(value);
            return value;
        });
        when(cases.findByCaseReference(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(savedCase.get()).filter(value -> value.getCaseReference().equals(invocation.getArgument(0))));
        when(allocations.save(any(SettlementRefundAllocation.class))).thenAnswer(invocation -> {
            SettlementRefundAllocation value = invocation.getArgument(0);
            savedAllocations.add(value);
            return value;
        });
        when(idempotency.execute(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Supplier<String> create = invocation.getArgument(2);
            return new SettlementIdempotencyResult(false, create.get());
        });
        when(booking.confirmedProjection("BK-1")).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100_000L, 100_000L, 50_000L, 50_000L, 1, List.of("session-uuid"), 1L));
        when(booking.verifyCustomerAccess(eq("BK-1"), any(), any(), any()))
                .thenReturn(new CustomerBookingAccessDecision(true));
        service = new CustomerBookingCancellationService(booking, operations, idempotency, cases, snapshots,
                audits, allocations, refunds, transactionManager);
    }

    @Test
    void eligibleMemberGetsWholeBookingRefundAllocatedOldestPaymentFirst() {
        when(operations.cancellationEligibility("BK-1")).thenReturn(eligibleIn(DurationHours.hours(96)));
        when(booking.verifiedDepositPayments("BK-1")).thenReturn(new VerifiedBookingDepositPaymentsProjection(List.of(
                payment("PAY-NEW", "20261030190000", 50_001L),
                payment("PAY-OLD", "20261030180000", 50_001L))));

        var result = service.open("BK-1", "cancel-key", "CORR-1", null, member());

        assertThat(result.status()).isEqualTo("PENDING_PROVIDER");
        assertThat(savedAllocations).extracting(SettlementRefundAllocation::getOriginalPaymentReference)
                .containsExactly("PAY-OLD", "PAY-NEW");
        assertThat(savedAllocations).extracting(SettlementRefundAllocation::getAllocatedAmountVnd)
                .containsExactly(50_001L, 50_001L);
        verify(refunds).dispatchCase(result.caseReference());
    }

    @Test
    void fiftyPercentPolicyRoundsAggregateBeforeAllocatingOldestPayment() {
        when(operations.cancellationEligibility("BK-1")).thenReturn(eligibleIn(DurationHours.hours(48)));
        when(booking.verifiedDepositPayments("BK-1")).thenReturn(new VerifiedBookingDepositPaymentsProjection(List.of(
                payment("PAY-OLD", "20261030180000", 50_001L),
                payment("PAY-NEW", "20261030190000", 50_001L))));

        service.open("BK-1", "cancel-key-2", "CORR-2", null, member());

        assertThat(savedAllocations).hasSize(1);
        assertThat(savedAllocations.getFirst().getOriginalPaymentReference()).isEqualTo("PAY-OLD");
        assertThat(savedAllocations.getFirst().getAllocatedAmountVnd()).isEqualTo(50_001L);
    }

    @Test
    void zeroRefundIsRejectedWithoutCreatingAllocationsOrDispatchingProvider() {
        when(operations.cancellationEligibility("BK-1")).thenReturn(eligibleIn(DurationHours.hours(12)));
        when(booking.verifiedDepositPayments("BK-1")).thenReturn(new VerifiedBookingDepositPaymentsProjection(
                List.of(payment("PAY-1", "20261030180000", 50_000L))));

        var result = service.open("BK-1", "cancel-key-3", "CORR-3", null, member());

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(savedCase.get().getReasonReference()).isEqualTo("ZERO_REFUND");
        assertThat(savedAllocations).isEmpty();
        verifyNoInteractions(refunds);
    }

    @Test
    void guestMustSupplyExactlyOneContactProof() {
        assertThatThrownBy(() -> service.open("BK-1", "guest-key", "CORR-4", new GuestContactProofDto(null, null), null))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessage("EXACTLY_ONE_GUEST_CONTACT_PROOF_REQUIRED");
        verify(booking, never()).verifyCustomerAccess(anyString(), any(), any(), any());
    }

    @Test
    void guestReadsSafeCaseOnlyAfterStoredContactProof() {
        savedCase.set(SettlementCase.builder().caseReference("CASE-1").bookingReference("BK-1")
                .caseType("CUSTOMER_CANCELLATION").lifecycleStatus("PENDING_PROVIDER")
                .correlationReference("CORR-1").build());

        var result = service.read("BK-1", "CASE-1", new GuestContactProofDto("guest@example.test", null), null);

        assertThat(result.caseReference()).isEqualTo("CASE-1");
        assertThat(result.status()).isEqualTo("PENDING_PROVIDER");
        assertThat(result.toString()).doesNotContain("guest@example.test");
        verify(booking).verifyCustomerAccess("BK-1", null, "guest@example.test", null);
    }

    @Test
    void memberMustOwnBookingToReadCancellationCase() {
        savedCase.set(SettlementCase.builder().caseReference("CASE-1").bookingReference("BK-1")
                .caseType("CUSTOMER_CANCELLATION").lifecycleStatus("PENDING_PROVIDER")
                .correlationReference("CORR-1").build());
        when(booking.verifyCustomerAccess("BK-1", 42L, null, null))
                .thenReturn(new CustomerBookingAccessDecision(false));

        assertThatThrownBy(() -> service.read("BK-1", "CASE-1", null, member()))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
    }

    private static CurrentAccountActor member() {
        return new CurrentAccountActor(42L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
    }

    private static VerifiedBookingPayment payment(String ref, String created, long amount) {
        return new VerifiedBookingPayment(ref, "12345678", created, amount, "VND", true);
    }

    private static BookingCancellationEligibilitySnapshot eligibleIn(java.time.Duration duration) {
        return new BookingCancellationEligibilitySnapshot(true, null, "session-uuid", Instant.now().plus(duration), false, false);
    }

    private static final class DurationHours {
        static java.time.Duration hours(long hours) { return java.time.Duration.ofHours(hours); }
    }
}
