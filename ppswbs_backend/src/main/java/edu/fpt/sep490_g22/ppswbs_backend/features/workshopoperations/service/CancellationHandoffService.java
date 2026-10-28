package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionCancellationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StandardizedReasonCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * T045: Service managing session cancellation and 003.3 settlement cancellation handoff.
 * <p>
 * Requirements (FR-007a, FR-009, FR-009a, Clarifications 2026-10-23/24):
 * - Restricted to Manager within canonical location scope (Staff is denied).
 * - Only permitted for pre-finalization sessions (DRAFT, PUBLISHED, SALES_PAUSED, IN_PROGRESS).
 *   Prohibited on terminal states (ATTENDANCE_FINALIZED, CANCELLED).
 * - Validates standardized reason code and mandatory note/evidence requirements.
 * - Commitment check:
 *   - If session has NO confirmed ticket AND NO active unexpired hold:
 *     Transitions directly to terminal CANCELLED and returns HTTP 200 representation.
 *   - If session has confirmed tickets OR active unexpired holds (commitments exist):
 *     Stops normal operation/check-in, transitions state to CANCELLATION_REQUESTED,
 *     records CancellationHandoff, persists a durable outbox event for the
 *     Settlement consumer, and returns semantic 202 (accepted locally).
 * - Enforces idempotency: duplicate requests return previous outcome without duplicate handoffs or audits.
 * - Leaves customer remedies, notification, reschedule, final-bill change, and refund to 003.3.
 * - Preserves immutable OperationsAuditEvent and maintains tracing correlation reference.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CancellationHandoffService {

    private final WorkshopSessionRepository sessionRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final CapacityHoldRepository holdRepository;
    private final CancellationHandoffRepository cancellationHandoffRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final SessionOperationalLeadRepository operationalLeadRepository;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final CancellationHandoffOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Cancellation operation result representing HTTP 200 vs 202 semantics.
     */
    @Getter
    @Builder
    public static class CancellationResult {
        private final int statusCode; // 200 (direct cancel) or 202 (handoff created)
        private final boolean handoffCreated;
        private final SessionResponse session; // present for 200 (or updated state)
        private final String cancellationCaseReference; // present for 202
        private final List<String> affectedBookingReferences;
        private final String sessionReference;

        public static CancellationResult directCancelled(SessionResponse session) {
            return CancellationResult.builder()
                    .statusCode(200)
                    .handoffCreated(false)
                    .session(session)
                    .sessionReference(session.getId())
                    .affectedBookingReferences(Collections.emptyList())
                    .build();
        }

        public static CancellationResult handoffCreated(String sessionRef, String caseRef, List<String> affectedBookings, SessionResponse session) {
            return CancellationResult.builder()
                    .statusCode(202)
                    .handoffCreated(true)
                    .session(session)
                    .sessionReference(sessionRef)
                    .cancellationCaseReference(caseRef)
                    .affectedBookingReferences(affectedBookings)
                    .build();
        }
    }

    /**
     * Cancels a workshop session or initiates a settlement cancellation handoff.
     */
    @Transactional
    public CancellationResult requestSessionCancellation(
            String authHeader,
            String sessionReference,
            SessionCancellationRequest request
    ) {
        return requestSessionCancellation(authHeader, null, null, sessionReference, request);
    }

    /**
     * Cancels a workshop session or initiates a settlement cancellation handoff with correlation and operation references.
     */
    @Transactional
    public CancellationResult requestSessionCancellation(
            String authHeader,
            String operationReference,
            String correlationId,
            String sessionReference,
            SessionCancellationRequest request
    ) {
        if (sessionReference == null || sessionReference.isBlank()) {
            throw new WorkshopValidationException("sessionReference is required");
        }
        if (request == null) {
            throw new WorkshopValidationException("SessionCancellationRequest must not be null");
        }

        validateCancellationReason(request);

        WorkshopSession session = resolveSession(sessionReference);

        // Guard: Authorization - strictly Manager within canonical location scope (Staff denied)
        AccountSummary actor = authorizationService.requireManagerLocationScope(authHeader, session.getLocation().getId());

        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();
        String effectiveOperationRef = (operationReference != null && !operationReference.isBlank())
                ? operationReference
                : UUID.randomUUID().toString();

        SessionState currentState = session.getState();

        // Guard: terminal state check
        if (currentState == SessionState.ATTENDANCE_FINALIZED) {
            log.warn("Cancellation rejected: session {} is in terminal state ATTENDANCE_FINALIZED", sessionReference);
            throw new WorkshopLifecycleException("Session is in terminal state ATTENDANCE_FINALIZED; cancellation is not permitted");
        }

        // Idempotency: already CANCELLED
        if (currentState == SessionState.CANCELLED) {
            log.info("Session {} is already CANCELLED; returning existing terminal outcome", sessionReference);
            return CancellationResult.directCancelled(toSessionResponse(session));
        }

        // Idempotency: already CANCELLATION_REQUESTED
        if (currentState == SessionState.CANCELLATION_REQUESTED) {
            log.info("Session {} is already in CANCELLATION_REQUESTED; returning existing handoff outcome", sessionReference);
            List<CancellationHandoff> existingHandoffs = cancellationHandoffRepository
                    .findBySessionIdAndStatus(session.getId(), CancellationHandoffStatus.REQUESTED);
            String existingCaseRef = existingHandoffs.isEmpty()
                    ? session.getCancellationHandoffReference()
                    : existingHandoffs.get(0).getCancellationCaseReference();

            List<String> affectedBookings = collectAffectedBookingReferences(session.getId(), Instant.now());
            return CancellationResult.handoffCreated(session.getReference(), existingCaseRef, affectedBookings, toSessionResponse(session));
        }

        // Commitments check: active confirmed tickets OR active unexpired holds
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        int confirmedSeats = entitlementRepository.countActiveConfirmedSeatsForSession(session.getId());
        int activeHoldSeats = holdRepository.sumActiveHoldsForSession(session.getId(), now);
        boolean hasCommitments = (confirmedSeats > 0) || (activeHoldSeats > 0);

        if (!hasCommitments) {
            // Path 1: Direct cancellation (HTTP 200) - No commitments exist
            session.setState(SessionState.CANCELLED);
            sessionRepository.save(session);

            recordAudit(
                    session.getId(),
                    actor.accountId(),
                    "SESSION_CANCELLED_DIRECT",
                    "SUCCESS",
                    effectiveCorrelationId,
                    String.format("{\"fromState\":\"%s\",\"toState\":\"CANCELLED\",\"hasCommitments\":false,\"reasonCode\":\"%s\"}",
                            currentState, request.getReasonCode().trim())
            );

            log.info("Session {} cancelled directly (no commitments) by Manager accountId={}", sessionReference, actor.accountId());
            return CancellationResult.directCancelled(toSessionResponse(session));
        }

        // Path 2: Cancellation Handoff (HTTP 202) - Commitments exist
        List<String> affectedBookingReferences = collectAffectedBookingReferences(session.getId(), now);

        String cancellationCaseReference = "CASE-" + session.getReference() + "-" + UUID.randomUUID().toString().substring(0, 8);

        // Update session state to CANCELLATION_REQUESTED
        session.setState(SessionState.CANCELLATION_REQUESTED);
        session.setCancellationHandoffReference(cancellationCaseReference);
        sessionRepository.save(session);

        // Record persistent CancellationHandoff entity
        CancellationHandoff handoff = CancellationHandoff.builder()
                .session(session)
                .requesterId(actor.accountId())
                .reasonCode(request.getReasonCode().trim())
                .reasonNote(request.getReasonNote())
                .evidenceReference(request.getEvidenceReference())
                .cancellationCaseReference(cancellationCaseReference)
                .status(CancellationHandoffStatus.REQUESTED)
                .requestedAt(now)
                .build();
        cancellationHandoffRepository.save(handoff);

        // Persist the handoff durably in the same transaction. Settlement consumes
        // this event asynchronously; the Operations response is accepted locally.
        String cancellationReasonRef = request.getReasonCode().trim()
                + (request.getReasonNote() != null && !request.getReasonNote().isBlank() ? ": " + request.getReasonNote() : "");

        enqueueSettlementHandoff(
                session.getReference(),
                affectedBookingReferences,
                cancellationReasonRef,
                effectiveOperationRef,
                effectiveCorrelationId,
                cancellationCaseReference
        );

        // Record immutable audit event
        recordAudit(
                session.getId(),
                actor.accountId(),
                "SESSION_CANCELLATION_REQUESTED",
                "SUCCESS",
                effectiveCorrelationId,
                String.format("{\"fromState\":\"%s\",\"toState\":\"CANCELLATION_REQUESTED\",\"hasCommitments\":true,\"cancellationCaseReference\":\"%s\",\"affectedBookingCount\":%d,\"reasonCode\":\"%s\"}",
                        currentState, cancellationCaseReference, affectedBookingReferences.size(), request.getReasonCode().trim())
        );

        log.info("Cancellation handoff created for session {} with {} affected bookings; caseRef={} by Manager accountId={}",
                sessionReference, affectedBookingReferences.size(), cancellationCaseReference, actor.accountId());

        return CancellationResult.handoffCreated(session.getReference(), cancellationCaseReference, affectedBookingReferences, toSessionResponse(session));
    }

    private void enqueueSettlementHandoff(
            String sessionReference,
            List<String> affectedBookings,
            String cancellationReasonRef,
            String operationReference,
            String correlationReference,
            String cancellationCaseReference
    ) {
        try {
            outboxRepository.save(CancellationHandoffOutboxEvent.builder()
                    .eventReference("CANCEL-HANDOFF-" + UUID.randomUUID())
                    .sessionReference(sessionReference)
                    .affectedBookingReferencesJson(objectMapper.writeValueAsString(affectedBookings))
                    .cancellationReasonReference(cancellationReasonRef)
                    .operationReference(operationReference)
                    .correlationReference(correlationReference)
                    .cancellationCaseReference(cancellationCaseReference)
                    .createdAt(Instant.now())
                    .build());
        } catch (JsonProcessingException ex) {
            throw new WorkshopLifecycleException("Cancellation handoff payload could not be persisted");
        }
    }

    private List<String> collectAffectedBookingReferences(Long sessionId, Instant now) {
        Set<String> bookingRefs = new LinkedHashSet<>();

        // 1. From active confirmed tickets
        List<TicketSessionEntitlement> entitlements = entitlementRepository.findBySessionId(sessionId);
        for (TicketSessionEntitlement entitlement : entitlements) {
            if (entitlement.getTicket().getValidityState() == TicketValidityState.ACTIVE) {
                if (entitlement.getTicket().getBookingReference() != null && !entitlement.getTicket().getBookingReference().isBlank()) {
                    bookingRefs.add(entitlement.getTicket().getBookingReference());
                }
            }
        }

        // 2. From active unexpired capacity holds
        List<CapacityHold> activeHolds = holdRepository.findBySessionIdAndState(sessionId, CapacityHoldState.ACTIVE);
        for (CapacityHold hold : activeHolds) {
            if (hold.getExpiresAt() != null && hold.getExpiresAt().isAfter(now)) {
                if (hold.getBookingReference() != null && !hold.getBookingReference().isBlank()) {
                    bookingRefs.add(hold.getBookingReference());
                }
            }
        }

        return new ArrayList<>(bookingRefs);
    }

    private void validateCancellationReason(SessionCancellationRequest request) {
        if (request.getReasonCode() == null || request.getReasonCode().isBlank()) {
            throw new WorkshopValidationException("reasonCode is required");
        }
        String trimmedCode = request.getReasonCode().trim();
        if (!StandardizedReasonCode.isRecognized(trimmedCode)) {
            throw new WorkshopValidationException("reasonCode '" + trimmedCode + "' is not recognized in the standardized catalogue");
        }

        StandardizedReasonCode code = null;
        for (StandardizedReasonCode c : StandardizedReasonCode.values()) {
            if (c.name().equalsIgnoreCase(trimmedCode)) {
                code = c;
                break;
            }
        }

        if (code != null) {
            boolean hasNote = request.getReasonNote() != null && !request.getReasonNote().isBlank();
            boolean hasEvidence = request.getEvidenceReference() != null && !request.getEvidenceReference().isBlank();

            if (code == StandardizedReasonCode.TICKET_REDELIVERY_UNAVAILABLE) {
                if (!hasNote || !hasEvidence) {
                    throw new WorkshopValidationException("For reasonCode TICKET_REDELIVERY_UNAVAILABLE, reasonNote and evidenceReference are required");
                }
            } else if (code.isRequiresNote() && !hasNote && !hasEvidence) {
                throw new WorkshopValidationException("For reasonCode " + code.name() + ", a descriptive reasonNote or evidenceReference is required");
            }
        }
    }

    private WorkshopSession resolveSession(String sessionReference) {
        return sessionRepository.findByReference(sessionReference)
                .or(() -> {
                    try {
                        return sessionRepository.findById(Long.parseLong(sessionReference));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                })
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop session not found with reference or ID: " + sessionReference));
    }

    private void recordAudit(Long sessionId, Long actorId, String action, String outcome, String correlationId, String metadata) {
        try {
            OperationsAuditEvent event = OperationsAuditEvent.builder()
                    .sessionId(sessionId)
                    .actorId(actorId)
                    .action(action)
                    .outcome(outcome)
                    .correlationId(correlationId)
                    .metadata(metadata)
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record cancellation audit event: {}", e.getMessage());
        }
    }

    private SessionResponse toSessionResponse(WorkshopSession session) {
        Long operationalLeadId = operationalLeadRepository.findBySessionIdAndActiveTrue(session.getId())
                .map(SessionOperationalLead::getManagerAccountId)
                .orElse(null);

        return SessionResponse.builder()
                .id(session.getReference())
                .locationId(session.getLocation().getId())
                .slotId(session.getSlot().getReference())
                .workshopDate(session.getWorkshopDate())
                .scheduledStartAt(session.getScheduledStartAt())
                .scheduledEndAt(session.getScheduledEndAt())
                .checkInOpensAt(session.getCheckInOpensAt())
                .checkInOpeningSource(session.getCheckInOpeningSource())
                .capacity(session.getCapacity())
                .state(session.getState())
                .operationalLeadAccountId(operationalLeadId)
                .build();
    }
}
