package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CancellationCompletionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCancellationCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketMlandRemedyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCancellationCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCaseStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.CancellationHandoffCompletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 003.3 T167 (FR-043; 003.2 FR-010a/SC-008): a resolved session cancellation is reported to 003.2. */
class CancellationHandoffCompletionServiceTest {

    private final SettlementCancellationCaseRepository cases = mock(SettlementCancellationCaseRepository.class);
    private final SettlementTicketMlandRemedyRepository remedies = mock(SettlementTicketMlandRemedyRepository.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
    private CancellationHandoffCompletionService service;

    private final String session = UUID.randomUUID().toString();
    private SettlementCancellationCase handoff;

    @BeforeEach
    void setUp() {
        service = new CancellationHandoffCompletionService(cases, remedies, operations, audits);
        handoff = SettlementCancellationCase.builder().caseReference("CASE-1").sessionReference(session)
                .affectedBookingReferencesJson("[]").cancellationReasonReference("R").operationReference("OP-1")
                .correlationReference("CORR-CASE").status(SettlementCaseStatus.OPEN).build();
        when(cases.findBySessionReferenceIgnoreCaseAndStatus(session, SettlementCaseStatus.OPEN)).thenReturn(List.of(handoff));
        when(cases.findByStatus(SettlementCaseStatus.OPEN)).thenReturn(List.of(handoff));
    }

    @Test
    @DisplayName("every ticket resolved -> 003.2 completeCancellation is called once with a deterministic operation reference")
    void allResolved_callsOnce() {
        when(operations.completeCancellation(eq(session), eq("CASE-1"), anyString(), anyString()))
                .thenReturn(CancellationCompletionOutcome.accepted());

        service.completeIfResolved(session, "CORR-1");

        verify(operations, times(1)).completeCancellation(session, "CASE-1", "SETTLE-CANCEL-DONE-CASE-1", "CORR-1");
        assertThat(handoff.getStatus()).isEqualTo(SettlementCaseStatus.RESOLVED);
        verify(audits).save(argThat(a -> "CANCELLATION_HANDOFF_COMPLETED".equals(a.getActionCode())));
    }

    @Test
    @DisplayName("one ticket still waiting for its buyer -> no call")
    void oneTicketPending_noCall() {
        when(remedies.existsByCancelledSessionReferenceIgnoreCaseAndOutcome(session, "PENDING_ANSWER")).thenReturn(true);

        service.completeIfResolved(session, "CORR-1");
        assertThat(service.sweep()).isZero();

        verify(operations, never()).completeCancellation(any(), any(), any(), any());
        assertThat(handoff.getStatus()).isEqualTo(SettlementCaseStatus.OPEN);
    }

    @Test
    @DisplayName("a handoff with no affected ticket is completed when it is received")
    void noTicketHandoff_completedOnReceipt() {
        when(operations.completeCancellation(any(), any(), any(), any())).thenReturn(CancellationCompletionOutcome.accepted());

        service.completeIfResolved(session, "CORR-RECEIVED");

        verify(operations, times(1)).completeCancellation(eq(session), eq("CASE-1"), anyString(), eq("CORR-RECEIVED"));
    }

    @Test
    @DisplayName("replay: a resolved case is not asked again, so there is no second call or effect")
    void replay_noSecondCall() {
        when(operations.completeCancellation(any(), any(), any(), any())).thenReturn(CancellationCompletionOutcome.accepted());
        service.completeIfResolved(session, "CORR-1");
        when(cases.findBySessionReferenceIgnoreCaseAndStatus(session, SettlementCaseStatus.OPEN)).thenReturn(List.of());
        when(cases.findByStatus(SettlementCaseStatus.OPEN)).thenReturn(List.of());

        service.completeIfResolved(session, "CORR-2");
        assertThat(service.sweep()).isZero();

        verify(operations, times(1)).completeCancellation(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CANCELLATION_HANDOFF_UNRESOLVED means not yet: no exception, case stays open, audited; a later call is ACCEPTED")
    void unresolved_isNotYet_thenAccepted() {
        when(operations.completeCancellation(any(), any(), any(), any()))
                .thenReturn(CancellationCompletionOutcome.rejected(RejectionReason.CANCELLATION_HANDOFF_UNRESOLVED))
                .thenReturn(CancellationCompletionOutcome.accepted());

        service.completeIfResolved(session, "CORR-1");

        assertThat(handoff.getStatus()).isEqualTo(SettlementCaseStatus.OPEN);
        verify(audits).save(argThat(a -> "CANCELLATION_HANDOFF_NOT_YET".equals(a.getActionCode())
                && "CANCELLATION_HANDOFF_UNRESOLVED".equals(a.getReasonReference())));

        // The next resolution (or the sweep) asks again with the same operation reference and is accepted.
        assertThat(service.sweep()).isEqualTo(1);
        assertThat(handoff.getStatus()).isEqualTo(SettlementCaseStatus.RESOLVED);
        verify(operations, times(2)).completeCancellation(eq(session), eq("CASE-1"), eq("SETTLE-CANCEL-DONE-CASE-1"), anyString());
    }

    @Test
    @DisplayName("any other rejection is audited and never thrown; the case stays open")
    void otherRejection_auditedNotThrown() {
        when(operations.completeCancellation(any(), any(), any(), any()))
                .thenReturn(CancellationCompletionOutcome.rejected(RejectionReason.INVALID_STATE));

        service.completeIfResolved(session, "CORR-1");

        assertThat(handoff.getStatus()).isEqualTo(SettlementCaseStatus.OPEN);
        verify(audits).save(argThat(a -> "CANCELLATION_HANDOFF_COMPLETION_REJECTED".equals(a.getActionCode())));
    }
}
