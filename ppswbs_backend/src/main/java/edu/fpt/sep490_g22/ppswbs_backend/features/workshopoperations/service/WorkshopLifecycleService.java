package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionOperationalLeadRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
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
    private final SessionStaffAssignmentRepository staffAssignmentRepository;
    private final SessionOperationalLeadRepository operationalLeadRepository;
    private final OperationsAuditEventRepository auditEventRepository;

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

        // 3. Enforce direct location scope at command time
        if (!authorizationService.isAllLocationActor(actor)) {
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

        // Only Manager in location scope or Admin can publish or resume sales
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

        // Only Manager in location scope or Admin can pause sales
        if (!authorizationService.isAllLocationActor(actor) && !authorizationService.isManager(actor)) {
            throw new WorkshopForbiddenException("Only a Manager can pause session sales");
        }
    }

    private void handleStartTransition(AccountSummary actor, WorkshopSession session, SessionState currentState) {
        // Can transition to IN_PROGRESS only from PUBLISHED or SALES_PAUSED
        if (currentState != SessionState.PUBLISHED && currentState != SessionState.SALES_PAUSED) {
            throw new WorkshopLifecycleException("Cannot transition to IN_PROGRESS from state " + currentState);
        }

        // Role check: must be active assigned Staff or recorded Manager operational lead (or Admin)
        if (!authorizationService.isAllLocationActor(actor)) {
            boolean isAssignedStaff = authorizationService.isStaff(actor) &&
                    staffAssignmentRepository.existsBySessionIdAndAccountIdAndActiveTrue(session.getId(), actor.accountId());
            boolean isOperationalLead = authorizationService.isManager(actor) &&
                    operationalLeadRepository.existsBySessionIdAndManagerAccountIdAndActiveTrue(session.getId(), actor.accountId());

            if (!isAssignedStaff && !isOperationalLead) {
                log.warn("Start onsite denied for accountId={}, role={}: neither assigned Staff nor recorded operational lead",
                        actor.accountId(), actor.role());
                throw new WorkshopForbiddenException("Only an assigned Staff member or recorded Manager operational lead may start session operation");
            }
        }

        // Responsibility invariant: Session MUST have at least one active responsible operator
        if (!responsibilityService.hasActiveResponsibleOperator(session.getId())) {
            throw new WorkshopLifecycleException("Cannot start session: session has no active responsible operator (assigned Staff or operational lead)");
        }
    }

    private void handleFinalizeTransition(AccountSummary actor, WorkshopSession session, SessionState currentState) {
        // Attendance can only be finalized from IN_PROGRESS
        if (currentState != SessionState.IN_PROGRESS) {
            throw new WorkshopLifecycleException("Cannot finalize attendance from state " + currentState + "; session must be IN_PROGRESS");
        }

        // Role check: assigned Staff or Manager in location scope (or Admin)
        if (!authorizationService.isAllLocationActor(actor)) {
            if (authorizationService.isStaff(actor)) {
                boolean isAssignedStaff = staffAssignmentRepository.existsBySessionIdAndAccountIdAndActiveTrue(session.getId(), actor.accountId());
                if (!isAssignedStaff) {
                    throw new WorkshopForbiddenException("Staff member is not assigned to session reference " + session.getReference());
                }
            } else if (!authorizationService.isManager(actor)) {
                throw new WorkshopForbiddenException("Access denied: Actor role " + actor.role() + " cannot finalize session attendance");
            }
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
