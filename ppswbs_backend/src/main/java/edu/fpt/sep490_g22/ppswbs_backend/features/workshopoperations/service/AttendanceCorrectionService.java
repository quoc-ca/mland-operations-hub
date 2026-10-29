package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceActionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * T044: Service managing Manager-only erroneous-check-in reversal and post-finalization attendance correction.
 * <p>
 * Requirements (FR-007, FR-007a, FR-008, Clarifications 2026-10-24):
 * - Erroneous-check-in reversal is permitted ONLY BEFORE attendance finalization (session in PUBLISHED or IN_PROGRESS).
 *   Reverts ticket attendance state back to NOT_CHECKED_IN and clears checkedInAt so it can be scanned again.
 * - Attendance correction is permitted ONLY AFTER attendance finalization (session in ATTENDANCE_FINALIZED).
 *   Post-finalization outcome is strictly limited to CHECKED_IN or NO_SHOW (NOT_CHECKED_IN is rejected).
 * - Restricted strictly to active Manager with global location authority.
 *   Staff is denied (WorkshopForbiddenException).
 * - Enforces standardized reason code validation and mandatory note/evidence requirements.
 * - Does NOT use or require 'workshop.checkin.override' authority.
 * - Does NOT directly inspect Firebase tokens, claims, or Account entities/DAOs (delegates to authorizationService).
 * - Preserves immutable audit history: retains original actions and appends new AttendanceAction and OperationsAuditEvent.
 * - Performs NO refund, settlement, reschedule, or customer notification side-effects.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceCorrectionService {

    private final WorkshopSessionRepository sessionRepository;
    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final AttendanceActionRepository attendanceActionRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final WorkshopOperationsAuthorizationService authorizationService;

    /**
     * Reverses an erroneous check-in before attendance finalization.
     * Reverts attendance state to NOT_CHECKED_IN and clears checkedInAt.
     */
    @Transactional
    public Ticket reverseCheckIn(String authHeader, String sessionReference, CheckInReversalRequest request) {
        return reverseCheckIn(authHeader, null, sessionReference, request);
    }

    /**
     * Reverses an erroneous check-in before attendance finalization with explicit correlation ID.
     */
    @Transactional
    public Ticket reverseCheckIn(String authHeader, String correlationId, String sessionReference, CheckInReversalRequest request) {
        if (sessionReference == null || sessionReference.isBlank()) {
            throw new WorkshopValidationException("sessionReference is required");
        }
        if (request == null) {
            throw new WorkshopValidationException("CheckInReversalRequest must not be null");
        }
        if (request.getTicketCode() == null || request.getTicketCode().isBlank()) {
            throw new WorkshopValidationException("ticketCode is required");
        }

        validateReason(request);

        WorkshopSession session = resolveSession(sessionReference);

        // Guard: check-in reversal is permitted only before attendance finalization
        if (session.getState() == SessionState.ATTENDANCE_FINALIZED || session.getState() == SessionState.CANCELLED) {
            log.warn("Check-in reversal rejected: session {} is in state {}", sessionReference, session.getState());
            throw new WorkshopLifecycleException("Session is in state " + session.getState()
                    + "; check-in reversal is only permitted before attendance finalization. Use attendance-corrections for finalized sessions.");
        }

        // Authorization: strictly active Manager globally (Staff denied)
        AccountSummary actor = authorizationService.requireGlobalManager(authHeader);

        AttendanceTicket ticket = ticketRepository.findByTicketCode(request.getTicketCode())
                .orElseThrow(() -> new WorkshopNotFoundException("Ticket not found for ticket code: " + request.getTicketCode()));

        if (ticket.getValidityState() != TicketValidityState.ACTIVE) {
            log.warn("Check-in reversal rejected: ticket {} validity state is {}", ticket.getTicketCode(), ticket.getValidityState());
            throw new WorkshopTicketConflictException("Ticket is " + ticket.getValidityState() + ", check-in reversal rejected");
        }

        TicketSessionEntitlement entitlement = entitlementRepository.findByTicketIdAndSessionIdWithLock(ticket.getId(), session.getId())
                .orElseThrow(() -> new WorkshopTicketConflictException("Ticket " + ticket.getTicketCode() + " is not entitled to this session and location"));

        if (entitlement.getAttendanceState() != AttendanceState.CHECKED_IN) {
            log.warn("Check-in reversal rejected: ticket {} is in state {}", ticket.getTicketCode(), entitlement.getAttendanceState());
            throw new WorkshopTicketConflictException("Ticket " + ticket.getTicketCode() + " is in state "
                    + entitlement.getAttendanceState() + "; check-in reversal requires currently CHECKED_IN ticket");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // 1. Revert attendance state to NOT_CHECKED_IN and clear checkedInAt
        entitlement.setAttendanceState(AttendanceState.NOT_CHECKED_IN);
        entitlement.setCheckedInAt(null);
        entitlementRepository.save(entitlement);

        // 2. Append immutable AttendanceAction
        AttendanceAction action = AttendanceAction.builder()
                .ticket(ticket)
                .session(session)
                .actionType(AttendanceActionType.REVERSAL)
                .actorId(actor.accountId())
                .channel("ONSITE_REVERSAL")
                .reasonCode(request.getReasonCode().trim())
                .reasonNote(request.getReasonNote())
                .evidenceReference(request.getEvidenceReference())
                .occurredAt(now)
                .build();
        attendanceActionRepository.save(action);

        // 3. Record OperationsAuditEvent
        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();

        recordAudit(
                session.getId(),
                actor.accountId(),
                "CHECK_IN_REVERSED",
                "SUCCESS",
                effectiveCorrelationId,
                String.format("{\"ticketCode\":\"%s\",\"fromState\":\"CHECKED_IN\",\"toState\":\"NOT_CHECKED_IN\",\"reasonCode\":\"%s\"}",
                        ticket.getTicketCode(), request.getReasonCode().trim())
        );

        log.info("Check-in reversed for ticket {} at session {} by Manager accountId={}",
                ticket.getTicketCode(), sessionReference, actor.accountId());

        return Ticket.builder()
                .ticketCode(ticket.getTicketCode())
                .sessionId(session.getReference())
                .locationId(session.getLocation().getId())
                .validityState(ticket.getValidityState())
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .checkedInAt(null)
                .build();
    }

    /**
     * Corrects attendance after whole-session attendance finalization.
     * Corrected outcome is strictly limited to CHECKED_IN or NO_SHOW.
     */
    @Transactional
    public Ticket correctFinalizedAttendance(String authHeader, String sessionReference, AttendanceCorrectionRequest request) {
        return correctFinalizedAttendance(authHeader, null, sessionReference, request);
    }

    /**
     * Corrects attendance after whole-session attendance finalization with explicit correlation ID.
     */
    @Transactional
    public Ticket correctFinalizedAttendance(String authHeader, String correlationId, String sessionReference, AttendanceCorrectionRequest request) {
        if (sessionReference == null || sessionReference.isBlank()) {
            throw new WorkshopValidationException("sessionReference is required");
        }
        if (request == null) {
            throw new WorkshopValidationException("AttendanceCorrectionRequest must not be null");
        }
        if (request.getTicketCode() == null || request.getTicketCode().isBlank()) {
            throw new WorkshopValidationException("ticketCode is required");
        }

        // Guard: post-finalization outcome must be CHECKED_IN or NO_SHOW (NOT_CHECKED_IN is rejected)
        if (request.getCorrectedAttendanceState() == null) {
            throw new WorkshopValidationException("correctedAttendanceState is required");
        }
        if (request.getCorrectedAttendanceState() != AttendanceState.CHECKED_IN
                && request.getCorrectedAttendanceState() != AttendanceState.NO_SHOW) {
            log.warn("Post-finalization correction rejected: invalid target state {}", request.getCorrectedAttendanceState());
            throw new WorkshopValidationException("correctedAttendanceState must be either CHECKED_IN or NO_SHOW; NOT_CHECKED_IN is not permitted after finalization");
        }

        validateReason(request);

        WorkshopSession session = resolveSession(sessionReference);

        // Guard: attendance correction is permitted only after attendance finalization
        if (session.getState() != SessionState.ATTENDANCE_FINALIZED) {
            log.warn("Attendance correction rejected: session {} is in state {}", sessionReference, session.getState());
            throw new WorkshopLifecycleException("Session is in state " + session.getState()
                    + "; attendance correction requires ATTENDANCE_FINALIZED. Use check-in-reversals before finalization.");
        }

        // Authorization: strictly active Manager globally (Staff denied)
        AccountSummary actor = authorizationService.requireGlobalManager(authHeader);

        AttendanceTicket ticket = ticketRepository.findByTicketCode(request.getTicketCode())
                .orElseThrow(() -> new WorkshopNotFoundException("Ticket not found for ticket code: " + request.getTicketCode()));

        if (ticket.getValidityState() != TicketValidityState.ACTIVE) {
            log.warn("Attendance correction rejected: ticket {} validity state is {}", ticket.getTicketCode(), ticket.getValidityState());
            throw new WorkshopTicketConflictException("Ticket is " + ticket.getValidityState() + ", attendance correction rejected");
        }

        TicketSessionEntitlement entitlement = entitlementRepository.findByTicketIdAndSessionIdWithLock(ticket.getId(), session.getId())
                .orElseThrow(() -> new WorkshopTicketConflictException("Ticket " + ticket.getTicketCode() + " is not entitled to this session and location"));

        AttendanceState previousState = entitlement.getAttendanceState();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // 1. Apply correction to entitlement
        entitlement.setAttendanceState(request.getCorrectedAttendanceState());
        if (request.getCorrectedAttendanceState() == AttendanceState.CHECKED_IN) {
            if (entitlement.getCheckedInAt() == null) {
                entitlement.setCheckedInAt(now);
            }
        } else {
            // NO_SHOW outcome clears checkedInAt
            entitlement.setCheckedInAt(null);
        }
        entitlementRepository.save(entitlement);

        // 2. Append immutable AttendanceAction
        AttendanceAction action = AttendanceAction.builder()
                .ticket(ticket)
                .session(session)
                .actionType(AttendanceActionType.CORRECTION)
                .actorId(actor.accountId())
                .channel("ONSITE_CORRECTION")
                .reasonCode(request.getReasonCode().trim())
                .reasonNote(request.getReasonNote())
                .evidenceReference(request.getEvidenceReference())
                .occurredAt(now)
                .build();
        attendanceActionRepository.save(action);

        // 3. Record OperationsAuditEvent
        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();

        recordAudit(
                session.getId(),
                actor.accountId(),
                "ATTENDANCE_CORRECTED",
                "SUCCESS",
                effectiveCorrelationId,
                String.format("{\"ticketCode\":\"%s\",\"fromState\":\"%s\",\"toState\":\"%s\",\"reasonCode\":\"%s\"}",
                        ticket.getTicketCode(), previousState, request.getCorrectedAttendanceState(), request.getReasonCode().trim())
        );

        log.info("Attendance corrected for ticket {} at session {} from {} to {} by Manager accountId={}",
                ticket.getTicketCode(), sessionReference, previousState, request.getCorrectedAttendanceState(), actor.accountId());

        return Ticket.builder()
                .ticketCode(ticket.getTicketCode())
                .sessionId(session.getReference())
                .locationId(session.getLocation().getId())
                .validityState(ticket.getValidityState())
                .attendanceState(entitlement.getAttendanceState())
                .checkedInAt(entitlement.getCheckedInAt())
                .build();
    }

    private void validateReason(ReasonedRequest request) {
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
            log.warn("Failed to record attendance correction audit event: {}", e.getMessage());
        }
    }
}
