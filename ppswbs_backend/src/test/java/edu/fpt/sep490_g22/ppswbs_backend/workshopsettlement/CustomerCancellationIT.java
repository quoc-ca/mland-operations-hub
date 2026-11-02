package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingDepositPaymentsProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCustomerCancellationSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundAllocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.GuestContactProofDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundAllocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.CustomerBookingCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingCancellationEligibilitySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerCancellationIT {
    @Autowired CustomerBookingCancellationService service;
    @Autowired SettlementCaseRepository cases;
    @Autowired SettlementCustomerCancellationSnapshotRepository snapshots;
    @Autowired SettlementRefundAllocationRepository allocations;

    @MockitoBean WorkshopBookingSettlementAdapter booking;
    @MockitoBean WorkshopOperationsSettlementAdapter operations;
    @MockitoBean WorkshopRefundService refunds;

    @BeforeEach
    void prepareUpstreamFacts() {
        when(booking.confirmedProjection("BK-IT-1")).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100_002L, 100_002L, 50_001L, 50_001L, 1, List.of("session-uuid"), 7L));
        when(booking.verifyCustomerAccess(eq("BK-IT-1"), any(), any(), any()))
                .thenReturn(new CustomerBookingAccessDecision(true));
        when(operations.cancellationEligibility("BK-IT-1")).thenReturn(new BookingCancellationEligibilitySnapshot(
                true, null, "session-uuid", Instant.now().plus(Duration.ofHours(96)), false, false));
        when(booking.verifiedDepositPayments("BK-IT-1")).thenReturn(new VerifiedBookingDepositPaymentsProjection(List.of(
                payment("PAY-NEW", "20261030190000", 50_001L),
                payment("PAY-OLD", "20261030180000", 50_001L))));
    }

    @Test
    void persistsOneWholeBookingClaimAndOldestFirstAllocationsBeforeRefundDispatch() {
        var result = service.open("BK-IT-1", "member-cancel-1", "CORR-IT-1", null, member());

        assertThat(result.status()).isEqualTo("PENDING_PROVIDER");
        assertThat(cases.findByCaseReference(result.caseReference())).get()
                .extracting(SettlementCase::getLifecycleStatus).isEqualTo("PENDING_PROVIDER");
        assertThat(snapshots.findByBookingReference("BK-IT-1")).isPresent();
        assertThat(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(result.caseReference()))
                .extracting(SettlementRefundAllocation::getOriginalPaymentReference)
                .containsExactly("PAY-OLD", "PAY-NEW");
        verify(refunds).dispatchCase(result.caseReference());
        verify(operations, never()).applyRefund(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void guestProofIsCheckedAndSecondCaseCannotAllocateAnotherRefund() {
        var proof = new GuestContactProofDto(null, "+84901234567");
        var first = service.open("BK-IT-1", "guest-cancel-1", "CORR-IT-2", proof, null);
        var replay = service.open("BK-IT-1", "guest-cancel-1", "CORR-IT-2", proof, null);

        assertThat(replay.caseReference()).isEqualTo(first.caseReference());
        assertThat(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(first.caseReference())).hasSize(2);
        assertThatThrownBy(() -> service.open("BK-IT-1", "guest-cancel-2", "CORR-IT-3", proof, null))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessage("CANCELLATION_CASE_ALREADY_EXISTS");
        assertThat(cases.count()).isEqualTo(1);
        assertThat(allocations.count()).isEqualTo(2);
        verify(booking, atLeastOnce()).verifyCustomerAccess("BK-IT-1", null, null, "+84901234567");
    }

    @Test
    void memberWithoutBookingOwnershipCannotOpenCancellationOrAllocateRefund() {
        when(booking.verifyCustomerAccess("BK-IT-1", 42L, null, null))
                .thenReturn(new CustomerBookingAccessDecision(false));

        assertThatThrownBy(() -> service.open("BK-IT-1", "member-denied", "CORR-IT-5", null, member()))
                .isInstanceOf(edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementNotFoundException.class);

        assertThat(cases.count()).isZero();
        assertThat(snapshots.count()).isZero();
        assertThat(allocations.count()).isZero();
        verifyNoInteractions(refunds);
        verify(operations, never()).applyRefund(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void operationsRejectionForPartialOrCheckedInCancellationCreatesNoRefundAllocation() {
        when(operations.cancellationEligibility("BK-IT-1")).thenReturn(
                BookingCancellationEligibilitySnapshot.rejected("PARTIAL_CANCELLATION_NOT_SUPPORTED"));

        var result = service.open("BK-IT-1", "partial-denied", "CORR-IT-6", null, member());

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(cases.findByCaseReference(result.caseReference())).get()
                .extracting(SettlementCase::getReasonReference).isEqualTo("PARTIAL_CANCELLATION_NOT_SUPPORTED");
        assertThat(snapshots.findByBookingReference("BK-IT-1")).get()
                .extracting(snapshot -> snapshot.getEligibilityOutcome()).isEqualTo("REJECTED");
        assertThat(allocations.count()).isZero();
        verifyNoInteractions(refunds);
        verify(operations, never()).applyRefund(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void ineligibleZeroRefundPersistsRejectionButCreatesNoAllocationOrProviderCall() {
        when(operations.cancellationEligibility("BK-IT-1")).thenReturn(new BookingCancellationEligibilitySnapshot(
                true, null, "session-uuid", Instant.now().plus(Duration.ofHours(12)), false, false));

        var result = service.open("BK-IT-1", "member-cancel-3", "CORR-IT-4", null, member());

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(allocations.count()).isZero();
        verifyNoInteractions(refunds);
        verify(operations, never()).applyRefund(anyString(), anyString(), anyString(), anyString());
    }

    private static CurrentAccountActor member() {
        return new CurrentAccountActor(42L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
    }

    private static VerifiedBookingPayment payment(String ref, String createDate, long amount) {
        return new VerifiedBookingPayment(ref, "12345678", createDate, amount, "VND", true);
    }
}
