package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CancellationCompletionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCancellationCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketMlandRemedyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCancellationCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCaseStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 003.3 T167 (FR-043, 003.2 FR-010a/SC-008): tells 003.2 that a Mland session cancellation is fully handled, so the
 * session leaves {@code CANCELLATION_REQUESTED}. It is called inside the transaction of each per-ticket resolution (a
 * replacement accepted, a refund request opened, or the answer/default of a ticket with no money recorded) and once
 * when the handoff is received. A ticket still waiting for its buyer means "not yet". 003.2 answers
 * {@code CANCELLATION_HANDOFF_UNRESOLVED} while any ticket still holds a seat there; that is also "not yet": nothing
 * is rolled back or failed, it is audited, and the next resolution or the sweep asks again. The operation reference
 * is the same for every attempt of one case, so a replay or a concurrent last resolution causes one transition.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CancellationHandoffCompletionService {

    static final String PENDING_ANSWER = "PENDING_ANSWER";

    private final SettlementCancellationCaseRepository cases;
    private final SettlementTicketMlandRemedyRepository remedies;
    private final WorkshopOperationsSettlementAdapter operations;
    private final SettlementBusinessAuditRepository audits;

    /** Called after a ticket of {@code sessionReference} was resolved (or its handoff received). */
    @Transactional
    public void completeIfResolved(String sessionReference, String correlationReference) {
        if (sessionReference == null || sessionReference.isBlank()) {
            return;
        }
        for (SettlementCancellationCase handoff
                : cases.findBySessionReferenceIgnoreCaseAndStatus(sessionReference.trim(), SettlementCaseStatus.OPEN)) {
            attempt(handoff, correlationReference, true);
        }
    }

    /** Asks again for every case still open; a case with a ticket still waiting is left alone. Returns completed cases. */
    @Transactional
    public int sweep() {
        int completed = 0;
        List<SettlementCancellationCase> open = cases.findByStatus(SettlementCaseStatus.OPEN);
        for (SettlementCancellationCase handoff : open) {
            if (attempt(handoff, handoff.getCorrelationReference(), false)) {
                completed++;
            }
        }
        return completed;
    }

    private boolean attempt(SettlementCancellationCase handoff, String correlationReference, boolean audit) {
        String session = handoff.getSessionReference();
        if (remedies.existsByCancelledSessionReferenceIgnoreCaseAndOutcome(session, PENDING_ANSWER)) {
            return false; // a ticket still waits for its buyer: not yet
        }
        try {
            UUID.fromString(session.trim());
        } catch (IllegalArgumentException invalid) {
            log.warn("Cancellation handoff not completed, the case session reference is not a UUID: case={}",
                    handoff.getCaseReference());
            return false;
        }
        String correlation = correlationReference == null || correlationReference.isBlank()
                ? handoff.getCorrelationReference() : correlationReference;
        CancellationCompletionOutcome outcome = operations.completeCancellation(session.trim(),
                handoff.getCaseReference(), operationReference(handoff), correlation);
        if (outcome.status() == OutcomeStatus.ACCEPTED) {
            handoff.setStatus(SettlementCaseStatus.RESOLVED);
            cases.save(handoff);
            record(handoff, "CANCELLATION_HANDOFF_COMPLETED", correlation, null);
            log.info("Cancellation handoff completed: case={}", handoff.getCaseReference());
            return true;
        }
        if (outcome.status() == OutcomeStatus.REJECTED && audit) {
            boolean notYet = outcome.reason() == RejectionReason.CANCELLATION_HANDOFF_UNRESOLVED;
            record(handoff, notYet ? "CANCELLATION_HANDOFF_NOT_YET" : "CANCELLATION_HANDOFF_COMPLETION_REJECTED",
                    correlation, outcome.reason() == null ? null : outcome.reason().name());
        }
        log.info("Cancellation handoff not completed yet: case={}, status={}, reason={}", handoff.getCaseReference(),
                outcome.status(), outcome.reason());
        return false;
    }

    /** One reference per case, so every attempt, replay or concurrent last resolution is the same operation. */
    static String operationReference(SettlementCancellationCase handoff) {
        return "SETTLE-CANCEL-DONE-" + handoff.getCaseReference();
    }

    private void record(SettlementCancellationCase handoff, String action, String correlation, String reason) {
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_CANCELLATION_CASE")
                .aggregateReference(handoff.getCaseReference())
                .actionCode(action)
                .actorReference("SYSTEM")
                .correlationReference(correlation)
                .reasonReference(reason)
                .build());
    }
}
