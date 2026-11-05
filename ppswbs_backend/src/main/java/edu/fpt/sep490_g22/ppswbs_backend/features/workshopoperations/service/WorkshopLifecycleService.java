package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StateTransitionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * T022: Service managing Workshop Session lifecycle state transitions.
 * Enforces allowed transitions across DRAFT, PUBLISHED, SALES_PAUSED, IN_PROGRESS,
 * ATTENDANCE_FINALIZED, CANCELLATION_REQUESTED, CANCELLED.
 * Enforces typed-safe rejections, sales-pause hold preservation, immutable audit,
 * and command-time actor and location scope validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopLifecycleService {

    private final AccountFacade accountFacade;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final WorkshopSessionRepository sessionRepository;
    private final WorkshopResponsibilityService responsibilityService;
    private final OperationsAuditEventRepository auditEventRepository;
    private final WorkshopOtaSnapshotService otaSnapshotService;

    @Transactional
    public SessionResponse transitionState(String authHeader, String correlationId, String sessionReference, StateTransitionRequest request) {
        if (request == null || request.getTargetState() == null) {
            throw new WorkshopValidationException("targetState is required");
        }

        SessionState targetState = request.getTargetState();
        if (targetState != SessionState.PUBLISHED &&
            targetState != SessionState.SALES_PAUSED &&
            targetState != SessionState.IN_PROGRESS &&
            targetState != SessionState.ATTENDANCE_FINALIZED) {
            throw new WorkshopLifecycleException("targetState must be one of: PUBLISHED, SALES_PAUSED, IN_PROGRESS, ATTENDANCE_FINALIZED; cancellation uses reasoned cancellation endpoints");
        }

        // 1. Authenticate actor and verify ACTIVE status at command time
        AccountSummary actor = authorizationService.authenticate(authHeader);
        ActorStatus currentStatus = accountFacade.getAccountStatus(actor.accountId())
                .orElseThrow(() -> new WorkshopForbiddenException("Actor account not found"));
        if (currentStatus != ActorStatus.ACTIVE) {
            log.warn("Command rejected: Actor accountId={} status is {}", actor.accountId(), currentStatus);
            throw new WorkshopForbiddenException("Actor account status is " + currentStatus + ", ACTIVE required");
        }

        // 2. Fetch session entity
        WorkshopSession session = requireSessionEntity(sessionReference);
        Long sessionLocationId = session.getLocation().getId();

        // 3. Enforce Staff location scope at command time; Managers are global.
        if (!authorizationService.isGlobalWorkshopActor(actor)) {
            List<Long> locationScope = accountFacade.getLocationScope(actor.accountId());
            if (locationScope == null || !locationScope.contains(sessionLocationId)) {
                log.warn("Location scope denied for accountId={} on session reference={} (locationId={})",
                        actor.accountId(), sessionReference, sessionLocationId);
                throw new WorkshopLocationScopeException("Location ID " + sessionLocationId + " is outside actor's active location scope");
            }
        }

        SessionState currentState = session.getState();

        // 4. Validate transition and role permissions
        if (currentState == targetState) {
            throw new WorkshopLifecycleException("Session is already in state " + currentState);
        }

        switch (targetState) {
            case PUBLISHED -> handlePublishTransition(actor, currentState);
            case SALES_PAUSED -> handlePauseTransition(actor, currentState);
            case IN_PROGRESS -> handleStartTransition(actor, session, currentState);
            case ATTENDANCE_FINALIZED -> handleFinalizeTransition(actor, session, currentState);
            default -> throw new WorkshopLifecycleException("Unsupported transition to " + targetState);
        }

        // 5. Update session state
        session.setState(targetState);
        sessionRepository.save(session);

        if (targetState == SessionState.PUBLISHED
                || targetState == SessionState.SALES_PAUSED
                || targetState == SessionState.IN_PROGRESS
                || targetState == SessionState.ATTENDANCE_FINALIZED
                || targetState == SessionState.CANCELLATION_REQUESTED
                || targetState == SessionState.CANCELLED) {
            otaSnapshotService.publishForObservableChange(session, UUID.randomUUID().toString(), java.time.Instant.now());
        }

        // 6. Record immutable audit event
        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank()) ? correlationId : UUID.randomUUID().toString();
        String metadata = String.format("{\"fromState\":\"%s\",\"toState\":\"%s\"}", currentState, targetState);
        recordAudit(session.getId(), actor.accountId(), "SESSION_STATE_TRANSITION", "SUCCESS", effectiveCorrelationId, metadata);

        log.info("Transitioned session reference={} from {} to {} by actorId={}, correlationId={}",
                sessionReference, currentState, targetState, actor.accountId(), effectiveCorrelationId);

        return responsibilityService.toSessionResponse(session);
    }

    private void handlePublishTransition(AccountSummary actor, SessionState currentState) {
        // Can only transition to PUBLISHED from DRAFT or SALES_PAUSED
        if (currentState != SessionState.DRAFT && currentState != SessionState.SALES_PAUSED) {
            throw new WorkshopLifecycleException("Cannot transition to PUBLISHED from state " + currentState);
        }

        // Active Manager or Admin can publish or resume sales globally.
        if (!authorizationService.isAllLocationActor(actor) && !authorizationService.isManager(actor)) {
            throw new WorkshopForbiddenException("Only a Manager can publish or resume sales for a session");
        }
        // Note: In DRAFT -> PUBLISHED, Staff assignment is explicitly not required before publication.
    }

    private void handlePauseTransition(AccountSummary actor, SessionState currentState) {
        // Can only transition to SALES_PAUSED from PUBLISHED
        if (currentState != SessionState.PUBLISHED) {
            throw new WorkshopLifecycleException("Cannot transition to SALES_PAUSED from state " + currentState);
        }

        // Active Manager or Admin can pause sales globally.
        if (!authorizationService.isAllLocationActor(actor) && !authorizationService.isManager(actor)) {
            throw new WorkshopForbiddenException("Only a Manager can pause session sales");
        }
    }

    private void handleStartTransition(AccountSummary actor, WorkshopSession session, SessionState currentState) {
        // Can transition to IN_PROGRESS only from PUBLISHED or SALES_PAUSED
        if (currentState != SessionState.PUBLISHED && currentState != SessionState.SALES_PAUSED) {
            throw new WorkshopLifecycleException("Cannot transition to IN_PROGRESS from state " + currentState);
        }

        if (!authorizationService.isGlobalWorkshopActor(actor)
                && !authorizationService.isStaff(actor)) {
            throw new WorkshopForbiddenException("Only an active Staff member in the session location or an active Manager may start session operation");
        }
    }

    private void handleFinalizeTransition(AccountSummary actor, WorkshopSession session, SessionState currentState) {
        // Attendance can only be finalized from IN_PROGRESS
        if (currentState != SessionState.IN_PROGRESS) {
            throw new WorkshopLifecycleException("Cannot finalize attendance from state " + currentState + "; session must be IN_PROGRESS");
        }

        // Role check: any active Staff in location scope or any active Manager.
        if (!authorizationService.isGlobalWorkshopActor(actor)
                && !authorizationService.isStaff(actor)) {
            throw new WorkshopForbiddenException("Access denied: Actor role " + actor.role() + " cannot finalize session attendance");
        }
    }

    private WorkshopSession requireSessionEntity(String sessionReference) {
        return sessionRepository.findByReference(sessionReference)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop session not found with reference " + sessionReference));
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
            log.warn("Failed to record audit event: action={}, error={}", action, e.getMessage());
        }
    }
}
