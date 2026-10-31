package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.TargetAccountSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionOperationalLeadRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.OperationalLeadRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StaffAssignmentRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionOperationalLead;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionStaffAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * T023: Service managing Staff assignment and Manager operational-lead responsibility.
 * Enforces canonical location scope, account existence, active status, role capability,
 * atomic lead handovers, and in-progress responsibility invariants.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopResponsibilityService {

    private final AccountFacade accountFacade;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final WorkshopSessionRepository sessionRepository;
    private final SessionStaffAssignmentRepository staffAssignmentRepository;
    private final SessionOperationalLeadRepository operationalLeadRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final WorkshopLocationService locationService;

    @Transactional
    public SessionResponse assignStaff(String authHeader, String correlationId, String sessionReference, StaffAssignmentRequest request) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        WorkshopSession session = requireSessionEntity(sessionReference);

        validateManagerAuthority(actor);

        if (request == null || request.getAccountId() == null) {
            throw new WorkshopValidationException("Staff assignment request and accountId must not be null");
        }

        Long targetAccountId = request.getAccountId();
        TargetAccountSnapshot targetAccount = accountFacade.getTargetAccount(targetAccountId)
                .orElseThrow(() -> new WorkshopLifecycleException("Target account ID " + targetAccountId + " does not exist"));

        if (targetAccount.canonicalRole() != ActorRole.STAFF) {
            throw new WorkshopLifecycleException("Target account must have canonical role STAFF, found " + targetAccount.canonicalRole());
        }

        if (targetAccount.status() != ActorStatus.ACTIVE) {
            throw new WorkshopLifecycleException("Target account is not ACTIVE, found " + targetAccount.status());
        }

        Long sessionLocationId = session.getLocation().getId();
        if (targetAccount.activeLocationIds() == null || !targetAccount.activeLocationIds().contains(sessionLocationId)) {
            log.warn("Target staff accountId={} has active locations {} outside session locationId={}",
                    targetAccountId, targetAccount.activeLocationIds(), sessionLocationId);
            throw new WorkshopLocationScopeException("Target staff account ID " + targetAccountId + " is outside active location scope for location ID " + sessionLocationId);
        }

        Optional<SessionStaffAssignment> existingAssignmentOpt =
                staffAssignmentRepository.findBySessionIdAndAccountIdAndActiveTrue(session.getId(), targetAccountId);

        if (existingAssignmentOpt.isEmpty()) {
            SessionStaffAssignment newAssignment = SessionStaffAssignment.builder()
                    .session(session)
                    .accountId(targetAccountId)
                    .assignedById(actor.accountId())
                    .active(true)
                    .assignedAt(Instant.now())
                    .build();
            staffAssignmentRepository.save(newAssignment);
        }

        recordAudit(session.getId(), actor.accountId(), "SESSION_STAFF_ASSIGNED", "SUCCESS", correlationId,
                "{\"assignedAccountId\":" + targetAccountId + "}");

        log.info("Assigned Staff accountId={} to session reference={} by actorId={}",
                targetAccountId, sessionReference, actor.accountId());

        return toSessionResponse(session);
    }

    @Transactional
    public void removeStaff(String authHeader, String correlationId, String sessionReference, Long targetAccountId) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        WorkshopSession session = requireSessionEntity(sessionReference);

        validateManagerAuthority(actor);

        if (targetAccountId == null) {
            throw new WorkshopValidationException("accountId is required for staff assignment removal");
        }

        Optional<SessionStaffAssignment> assignmentOpt =
                staffAssignmentRepository.findBySessionIdAndAccountIdAndActiveTrue(session.getId(), targetAccountId);

        if (assignmentOpt.isEmpty()) {
            return; // Idempotent
        }

        // Invariant during IN_PROGRESS: cannot remove final responsible operator without an active operational lead
        if (session.getState() == SessionState.IN_PROGRESS) {
            boolean hasActiveLead = hasActiveOperationalLead(session.getId());
            if (!hasActiveLead) {
                long otherActiveStaffCount = staffAssignmentRepository.findBySessionIdAndActiveTrue(session.getId()).stream()
                        .filter(a -> !a.getAccountId().equals(targetAccountId))
                        .filter(a -> accountFacade.getTargetAccount(a.getAccountId())
                                .map(s -> s.status() == ActorStatus.ACTIVE)
                                .orElse(false))
                        .count();

                if (otherActiveStaffCount == 0) {
                    throw new WorkshopLifecycleException(
                            "Cannot remove final responsible Staff during IN_PROGRESS unless an active Manager operational lead remains"
                    );
                }
            }
        }

        SessionStaffAssignment assignment = assignmentOpt.get();
        assignment.setActive(false);
        assignment.setRevokedAt(Instant.now());
        staffAssignmentRepository.save(assignment);

        recordAudit(session.getId(), actor.accountId(), "SESSION_STAFF_REMOVED", "SUCCESS", correlationId,
                "{\"removedAccountId\":" + targetAccountId + "}");

        log.info("Removed Staff accountId={} from session reference={} by actorId={}",
                targetAccountId, sessionReference, actor.accountId());
    }

    @Transactional
    public SessionResponse assignOperationalLead(String authHeader, String correlationId, String sessionReference, OperationalLeadRequest request) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        WorkshopSession session = requireSessionEntity(sessionReference);

        validateManagerAuthority(actor);

        if (request == null || request.getManagerAccountId() == null) {
            throw new WorkshopValidationException("Operational lead request and managerAccountId are required");
        }
        if (request.getReasonCode() == null || request.getReasonCode().isBlank()) {
            throw new WorkshopValidationException("reasonCode is required for operational lead assignment");
        }

        // Permitted states: DRAFT, PUBLISHED, SALES_PAUSED, IN_PROGRESS
        SessionState currentState = session.getState();
        if (currentState != SessionState.DRAFT &&
            currentState != SessionState.PUBLISHED &&
            currentState != SessionState.SALES_PAUSED &&
            currentState != SessionState.IN_PROGRESS) {
            throw new WorkshopLifecycleException("Cannot take or handover operational lead in session state " + currentState);
        }

        Long targetManagerAccountId = request.getManagerAccountId();
        TargetAccountSnapshot targetAccount = accountFacade.getTargetAccount(targetManagerAccountId)
                .orElseThrow(() -> new WorkshopLifecycleException("Target manager account ID " + targetManagerAccountId + " does not exist"));

        if (targetAccount.canonicalRole() != ActorRole.MANAGER && targetAccount.canonicalRole() != ActorRole.ADMIN) {
            throw new WorkshopLifecycleException("Target account must have canonical role MANAGER, found " + targetAccount.canonicalRole());
        }

        if (targetAccount.status() != ActorStatus.ACTIVE) {
            throw new WorkshopLifecycleException("Target manager account is not ACTIVE, found " + targetAccount.status());
        }

        // Atomic handover: deactivate existing active lead and activate incoming lead in one transaction
        Optional<SessionOperationalLead> currentLeadOpt = operationalLeadRepository.findBySessionIdAndActiveTrue(session.getId());
        Long outgoingLeadId = null;

        if (currentLeadOpt.isPresent()) {
            SessionOperationalLead currentLead = currentLeadOpt.get();
            outgoingLeadId = currentLead.getManagerAccountId();
            currentLead.setActive(false);
            currentLead.setRevokedAt(Instant.now());
            operationalLeadRepository.save(currentLead);
        }

        SessionOperationalLead newLead = SessionOperationalLead.builder()
                .session(session)
                .managerAccountId(targetManagerAccountId)
                .active(true)
                .reasonCode(request.getReasonCode())
                .reasonNote(request.getReasonNote())
                .evidenceReference(request.getEvidenceReference())
                .assignedAt(Instant.now())
                .build();
        operationalLeadRepository.save(newLead);

        String action = outgoingLeadId != null ? "OPERATIONAL_LEAD_HANDOVER" : "OPERATIONAL_LEAD_ASSIGNED";
        String metadata = String.format("{\"outgoingLeadId\":%s,\"incomingLeadId\":%d,\"reasonCode\":\"%s\"}",
                outgoingLeadId != null ? outgoingLeadId.toString() : "null",
                targetManagerAccountId,
                request.getReasonCode());

        recordAudit(session.getId(), actor.accountId(), action, "SUCCESS", correlationId, metadata);

        log.info("Operational lead set for session reference={}: outgoing={}, incoming={} by actorId={}",
                sessionReference, outgoingLeadId, targetManagerAccountId, actor.accountId());

        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveResponsibleOperator(Long sessionId) {
        // 1. Check active operational lead
        if (hasActiveOperationalLead(sessionId)) {
            return true;
        }

        // 2. Check active assigned staff
        List<SessionStaffAssignment> staffList = staffAssignmentRepository.findBySessionIdAndActiveTrue(sessionId);
        for (SessionStaffAssignment staff : staffList) {
            Optional<TargetAccountSnapshot> targetOpt = accountFacade.getTargetAccount(staff.getAccountId());
            if (targetOpt.isPresent() &&
                targetOpt.get().status() == ActorStatus.ACTIVE &&
                targetOpt.get().canonicalRole() == ActorRole.STAFF) {
                return true;
            }
        }

        return false;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveOperationalLead(Long sessionId) {
        Optional<SessionOperationalLead> leadOpt = operationalLeadRepository.findBySessionIdAndActiveTrue(sessionId);
        if (leadOpt.isEmpty()) {
            return false;
        }
        Long mgrId = leadOpt.get().getManagerAccountId();
        Optional<TargetAccountSnapshot> targetOpt = accountFacade.getTargetAccount(mgrId);
        return targetOpt.isPresent() &&
               targetOpt.get().status() == ActorStatus.ACTIVE &&
               (targetOpt.get().canonicalRole() == ActorRole.MANAGER || targetOpt.get().canonicalRole() == ActorRole.ADMIN);
    }

    private void validateManagerAuthority(AccountSummary actor) {
        if (authorizationService.isAllLocationActor(actor)) {
            return;
        }
        if (!authorizationService.isManager(actor)) {
            throw new WorkshopForbiddenException("Access requires MANAGER role");
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
                    .correlationId(correlationId != null && !correlationId.isBlank() ? correlationId : UUID.randomUUID().toString())
                    .metadata(metadata)
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record audit event: action={}, error={}", action, e.getMessage());
        }
    }

    public SessionResponse toSessionResponse(WorkshopSession session) {
        Long operationalLeadAccountId = operationalLeadRepository.findBySessionIdAndActiveTrue(session.getId())
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
                .operationalLeadAccountId(operationalLeadAccountId)
                .build();
    }
}
