package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingDepositPaymentsProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.MlandRemedyCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class MlandCancellationRemedyIT {
    @Autowired MlandCancellationRemedyService service;
    @Autowired SettlementCancellationCaseRepository cancellationCases;
    @Autowired SettlementBookingMlandRefundPolicySnapshotRepository policySnapshots;
    @Autowired SettlementMlandRemedySnapshotRepository remedySnapshots;
    @Autowired SettlementRefundAllocationRepository allocations;
    @Autowired SettlementCaseRepository cases;
    @Autowired SettlementRefundPolicyRevisionRepository revisions;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean WorkshopBookingSettlementAdapter booking;
    @MockitoBean WorkshopSettlementAuthorizationService authorization;
    @MockitoBean WorkshopRefundService refunds;

    private CurrentAccountActor manager;

    @BeforeEach
    void configureManager() {
        manager = new CurrentAccountActor(77L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
        when(authorization.requireManager(manager)).thenReturn(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNAVAILABLE", "DECLINED"})
    void replacementMustHaveUnavailableOrDeclinedOutcomeThenRefundsPartialAmountsOldestFirst(String outcome) {
        Facts facts = persistFacts(50);
        revisions.saveAndFlush(SettlementRefundPolicyRevision.builder()
                .policyReference("FUTURE-" + facts.bookingReference())
                .effectiveAt(facts.confirmedAt().plusSeconds(86_400))
                .wholeBookingRefundPercent(0).createdByAccountId(manager.accountId()).build());
        when(booking.confirmedProjection(facts.bookingReference())).thenReturn(confirmedProjection());
        when(booking.verifiedDepositPayments(facts.bookingReference())).thenReturn(
                new VerifiedBookingDepositPaymentsProjection(List.of(
                        payment("PAY-NEW-" + facts.id(), "20261031120000", 60_001L),
                        payment("PAY-OLD-" + facts.id(), "20261031110000", 40_000L))));

        var result = service.open(facts.bookingReference(), "key-" + facts.id(), "corr-" + facts.id(),
                new MlandRemedyCommandDto("Mland cancelled a required session", outcome,
                        facts.handoffReference()), manager);

        var remedy = remedySnapshots.findByBookingReference(facts.bookingReference()).orElseThrow();
        assertThat(result.status()).isEqualTo("PENDING_PROVIDER");
        assertThat(remedy.getCancellationHandoffReference()).isEqualTo(facts.handoffReference());
        assertThat(remedy.getReplacementOutcome()).isEqualTo(outcome);
        assertThat(remedy.getPolicyReference()).isEqualTo("POLICY-50-" + facts.id());
        assertThat(remedy.getRefundPercent()).isEqualTo((short) 50);
        assertThat(remedy.getConfirmedAt()).isEqualTo(facts.confirmedAt());
        assertThat(remedy.getCollectedAmountVnd()).isEqualTo(100_001L);
        assertThat(remedy.getRefundableAmountVnd()).isEqualTo(50_000L);
        assertThat(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(result.caseReference()))
                .extracting(SettlementRefundAllocation::getOriginalPaymentReference)
                .containsExactly("PAY-OLD-" + facts.id(), "PAY-NEW-" + facts.id());
        assertThat(allocations.findAllByCaseReferenceOrderByCreatedAtAsc(result.caseReference()))
                .extracting(SettlementRefundAllocation::getAllocatedAmountVnd)
                .containsExactly(40_000L, 10_000L);
        verify(refunds).dispatchCase(result.caseReference());
    }

    @Test
    void missingBookingTimePolicySnapshotFailsClosedWithoutCreatingCaseOrAllocations() {
        Facts facts = persistHandoffOnly();
        when(booking.confirmedProjection(facts.bookingReference())).thenReturn(confirmedProjection());

        assertThatThrownBy(() -> service.open(facts.bookingReference(), "missing-policy-" + facts.id(),
                "corr-" + facts.id(), new MlandRemedyCommandDto("reason", "UNAVAILABLE",
                        facts.handoffReference()), manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessage("BOOKING_REFUND_POLICY_SNAPSHOT_NOT_READY");

        assertThat(remedySnapshots.findByBookingReference(facts.bookingReference())).isEmpty();
        assertThat(cases.findAll()).noneMatch(item -> facts.bookingReference().equals(item.getBookingReference()));
        verifyNoInteractions(refunds);
    }

    @Test
    void cancellationHandoffMustNameTheSameBookingBeforeRefundCanBeOpened() {
        Facts facts = persistFacts(100);
        writeJsonArray(facts.handoffReference(), "OTHER-BOOKING");
        when(booking.confirmedProjection(facts.bookingReference())).thenReturn(confirmedProjection());

        assertThatThrownBy(() -> service.open(facts.bookingReference(), "mismatch-" + facts.id(),
                "corr-" + facts.id(), new MlandRemedyCommandDto("reason", "DECLINED",
                        facts.handoffReference()), manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessage("CANCELLATION_HANDOFF_BOOKING_MISMATCH");

        assertThat(remedySnapshots.findByBookingReference(facts.bookingReference())).isEmpty();
        verifyNoInteractions(refunds);
    }

    @Test
    void remedyCannotBeOpenedWhileReplacementIsStillPendingOrUnspecified() {
        String bookingReference = "BK-MLAND-PENDING-" + UUID.randomUUID();

        assertThatThrownBy(() -> service.open(bookingReference, "pending-replacement", "corr-pending",
                new MlandRemedyCommandDto("reason", "OFFERED", "HANDOFF-PENDING"), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessage("replacementOutcome must be UNAVAILABLE or DECLINED");

        assertThat(cases.findAll()).noneMatch(item -> bookingReference.equals(item.getBookingReference()));
        verifyNoInteractions(booking, refunds);
    }

    private Facts persistFacts(int refundPercent) {
        Facts facts = persistHandoffOnly();
        policySnapshots.saveAndFlush(SettlementBookingMlandRefundPolicySnapshot.builder()
                .snapshotReference("POLICY-SNAPSHOT-" + facts.id())
                .bookingReference(facts.bookingReference())
                .policyReference("POLICY-" + refundPercent + "-" + facts.id())
                .refundPercent((short) refundPercent).confirmedAt(facts.confirmedAt()).build());
        return facts;
    }

    private Facts persistHandoffOnly() {
        String id = UUID.randomUUID().toString();
        String bookingReference = "BK-MLAND-" + id;
        String handoffReference = "HANDOFF-MLAND-" + id;
        Instant confirmedAt = Instant.parse("2026-10-31T10:00:00Z");
        cancellationCases.saveAndFlush(SettlementCancellationCase.builder()
                .caseReference(handoffReference).sessionReference("SESSION-" + id)
                .affectedBookingReferencesJson("[\"" + bookingReference + "\"]")
                .cancellationReasonReference("REASON-" + id).operationReference("OP-" + id)
                .correlationReference("CORR-" + id).status(SettlementCaseStatus.OPEN).build());
        writeJsonArray(handoffReference, bookingReference);
        assertThat(cancellationCases.findByCaseReference(handoffReference).orElseThrow()
                .getAffectedBookingReferencesJson()).isEqualTo("[\"" + bookingReference + "\"]");
        return new Facts(id, bookingReference, handoffReference, confirmedAt);
    }

    private void writeJsonArray(String handoffReference, String bookingReference) {
        jdbc.update("UPDATE settlement_cancellation_cases SET affected_booking_references = JSON '[\""
                + bookingReference + "\"]' WHERE case_reference = ?", handoffReference);
    }

    private static ConfirmedBookingFinancialProjection confirmedProjection() {
        return new ConfirmedBookingFinancialProjection("PACKAGE-1", 100_001L, 100_001L,
                50_001L, 50_000L, 1, List.of("SESSION-1"), 1L);
    }

    private static VerifiedBookingPayment payment(String reference, String createdAt, long amount) {
        return new VerifiedBookingPayment(reference, "1234567", createdAt, amount, "VND", true);
    }

    private record Facts(String id, String bookingReference, String handoffReference, Instant confirmedAt) { }
}
