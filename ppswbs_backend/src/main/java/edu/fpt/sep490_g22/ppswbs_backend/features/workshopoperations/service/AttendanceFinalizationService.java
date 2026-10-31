package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceActionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionOperationalLeadRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AttendanceFinalizationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * T043: Service implementing atomic whole-session attendance finalization.
 * <p>
 * Requirements (FR-006, FR-007, FR-010):
 * - Applies to the whole session in one atomic action.
 * - Allowed only to the actively assigned Staff member or recorded Manager operational lead (or Admin).
 * - Allowed only while the session is IN_PROGRESS.
 * - Requires explicit confirmation that the session has actually ended onsite (confirmedSessionEnded = true).
 * - Converts every active, unchecked ticket entitlement for this session to NO_SHOW.
 * - Leaves already CHECKED_IN tickets intact with their original checked-in timestamp.
 * - Transitions session state from IN_PROGRESS to ATTENDANCE_FINALIZED.
 * - Records an immutable audit event and attendance actions.
 * - Performs NO refund, settlement, reschedule, customer notification, or cancellation handoff.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceFinalizationService {

    private final WorkshopSessionRepository sessionRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final AttendanceActionRepository attendanceActionRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final WorkshopOtaSnapshotService otaSnapshotService;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final SessionOperationalLeadRepository operationalLeadRepository;

    /**
     * Finalizes whole-session attendance atomically with default correlation ID.
     */
    @Transactional
    public SessionResponse finalizeAttendance(String authHeader, String sessionReference, AttendanceFinalizationRequest request) {
        return finalizeAttendance(authHeader, null, sessionReference, request);
    }

    /**
     * Finalizes whole-session attendance atomically.
     *
     * @param authHeader       Bearer identity token
     * @param correlationId    Distributed tracing correlation ID
     * @param sessionReference Opaque session reference or ID
     * @param request          Finalization request containing confirmedSessionEnded confirmation
     * @return Updated SessionResponse in ATTENDANCE_FINALIZED state
     */
    @Transactional
    public SessionResponse finalizeAttendance(String authHeader, String correlationId, String sessionReference, AttendanceFinalizationRequest request) {
        if (sessionReference == null || sessionReference.isBlank()) {
            throw new WorkshopValidationException("sessionReference is required");
        }

        if (request == null || !Boolean.TRUE.equals(request.getConfirmedSessionEnded())) {
            log.warn("Finalization rejected: confirmedSessionEnded is false or null for session {}", sessionReference);
            throw new WorkshopValidationException("Explicit confirmation that session has actually ended onsite is required (confirmedSessionEnded=true)");
        }

        WorkshopSession session = resolveSessionForUpdate(sessionReference);

        // Guard: session must be IN_PROGRESS
        if (session.getState() != SessionState.IN_PROGRESS) {
            log.warn("Finalization rejected: session {} is in state {}", sessionReference, session.getState());
            throw new WorkshopLifecycleException("Session is in state " + session.getState() + "; finalization requires IN_PROGRESS");
        }

        // Authorization: assigned Staff in location scope or any active Manager globally.
        AccountSummary actor = authorizationService.requireStaffSessionAssignment(authHeader, session.getId());

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // 1. Transition session state to ATTENDANCE_FINALIZED
        session.setState(SessionState.ATTENDANCE_FINALIZED);
        session.setAttendanceFinalizedAt(now);
        sessionRepository.save(session);
        otaSnapshotService.publishForObservableChange(session, "ATTENDANCE_FINALIZED", now);

        // 2. Atomic conversion: convert all active unchecked tickets for this session to NO_SHOW
        List<TicketSessionEntitlement> entitlements = entitlementRepository.findBySessionId(session.getId());
        int convertedNoShowCount = 0;
        int preservedCheckedInCount = 0;

        for (TicketSessionEntitlement entitlement : entitlements) {
            if (entitlement.getTicket().getValidityState() != TicketValidityState.ACTIVE) {
                // Cancelled, revoked, or refunded tickets are not active attendees
                continue;
            }

            if (entitlement.getAttendanceState() == AttendanceState.NOT_CHECKED_IN) {
                entitlement.setAttendanceState(AttendanceState.NO_SHOW);
                entitlementRepository.save(entitlement);

                AttendanceAction action = AttendanceAction.builder()
                        .ticket(entitlement.getTicket())
                        .session(session)
                        .actionType(AttendanceActionType.FINALIZATION)
                        .actorId(actor.accountId())
                        .channel("ONSITE_FINALIZATION")
                        .reasonCode("SESSION_FINALIZED_NO_SHOW")
                        .reasonNote(request.getNote() != null && !request.getNote().isBlank()
                                ? request.getNote()
                                : "Attendance finalized: unchecked ticket marked NO_SHOW")
                        .occurredAt(now)
                        .build();
                attendanceActionRepository.save(action);
                convertedNoShowCount++;
            } else if (entitlement.getAttendanceState() == AttendanceState.CHECKED_IN) {
                // Preserved intact - do NOT modify checkedInAt or attendanceState
                preservedCheckedInCount++;
            }
        }

        // 3. Record immutable audit event
        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();

        recordAudit(
                session.getId(),
                actor.accountId(),
                "ATTENDANCE_FINALIZED",
                "SUCCESS",
                effectiveCorrelationId,
                String.format("{\"fromState\":\"IN_PROGRESS\",\"toState\":\"ATTENDANCE_FINALIZED\",\"confirmedEnded\":true,\"noShowCount\":%d,\"checkedInCount\":%d}",
                        convertedNoShowCount, preservedCheckedInCount)
        );

        log.info("Attendance finalized for session {} by actorId={}: {} tickets marked NO_SHOW, {} checked-in preserved",
                sessionReference, actor.accountId(), convertedNoShowCount, preservedCheckedInCount);

        return toSessionResponse(session);
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

    private WorkshopSession resolveSessionForUpdate(String sessionReference) {
        return sessionRepository.findByReferenceWithLock(sessionReference)
                .or(() -> {
                    try {
                        return sessionRepository.findByIdWithLock(Long.parseLong(sessionReference));
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
            log.warn("Failed to record finalization audit event: {}", e.getMessage());
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
