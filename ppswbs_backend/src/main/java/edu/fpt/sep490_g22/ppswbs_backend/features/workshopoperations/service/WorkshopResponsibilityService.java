package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.TargetAccountSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StaffAssignmentRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionStaffAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * T023: Service managing Staff assignment to a session (Manager only).
 * Enforces account existence, ACTIVE status, STAFF role and location scope (T130), and the
 * last-assignment confirmation rule (T115). There is no operational-lead role any more (T124).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopResponsibilityService {

    /** States in which Staff may be assigned (T130). Revocation is allowed in every state. */
    private static final Set<SessionState> ASSIGNABLE_STATES = EnumSet.of(
            SessionState.DRAFT, SessionState.PUBLISHED, SessionState.SALES_PAUSED, SessionState.IN_PROGRESS);

    /** States in which `missingStaff` and the last-assignment guard apply (T115, CHK030). */
    private static final Set<SessionState> STAFFING_WATCHED_STATES = EnumSet.of(
            SessionState.PUBLISHED, SessionState.SALES_PAUSED, SessionState.IN_PROGRESS);

    private final WorkshopOperationsAuthorizationService authorizationService;
    private final WorkshopSessionRepository sessionRepository;
    private final SessionStaffAssignmentRepository staffAssignmentRepository;
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

        if (!ASSIGNABLE_STATES.contains(session.getState())) {
            throw new WorkshopLifecycleException("Cannot assign Staff to a session in state " + session.getState());
        }

        Long targetAccountId = request.getAccountId();
        TargetAccountSnapshot targetAccount = authorizationService.getTargetAccount(targetAccountId)
                .orElseThrow(() -> new WorkshopStaffNotEligibleException(
                        WorkshopStaffNotEligibleException.Reason.ACCOUNT_NOT_FOUND,
                        "Target account ID " + targetAccountId + " does not exist"));

        if (targetAccount.canonicalRole() != ActorRole.STAFF) {
            throw new WorkshopStaffNotEligibleException(WorkshopStaffNotEligibleException.Reason.NOT_STAFF,
                    "Target account must have canonical role STAFF, found " + targetAccount.canonicalRole());
        }

        if (targetAccount.status() != ActorStatus.ACTIVE) {
            throw new WorkshopStaffNotEligibleException(WorkshopStaffNotEligibleException.Reason.NOT_ACTIVE,
                    "Target account is not ACTIVE, found " + targetAccount.status());
        }

        Long sessionLocationId = session.getLocation().getId();
        if (targetAccount.activeLocationIds() == null || !targetAccount.activeLocationIds().contains(sessionLocationId)) {
            log.warn("Target staff accountId={} has active locations {} outside session locationId={}",
                    targetAccountId, targetAccount.activeLocationIds(), sessionLocationId);
            throw new WorkshopStaffNotEligibleException(WorkshopStaffNotEligibleException.Reason.LOCATION_NOT_IN_SCOPE,
                    "Target staff account ID " + targetAccountId + " is outside active location scope for location ID " + sessionLocationId);
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

    /**
     * Revokes a Staff assignment. Revocation is allowed in every session state; removing the LAST active
     * assignment of a PUBLISHED, SALES_PAUSED or IN_PROGRESS session at or after {@code checkInOpensAt} needs
     * {@code confirmLastAssignmentRemoval} (T115). The refusal is audited and survives the refusal exception.
     */
    @Transactional(noRollbackFor = WorkshopLastStaffAssignmentException.class)
    public void removeStaff(String authHeader, String correlationId, String sessionReference, Long targetAccountId,
                            boolean confirmLastAssignmentRemoval) {
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

        boolean lastAssignment = staffAssignmentRepository.findBySessionIdAndActiveTrue(session.getId()).stream()
                .allMatch(a -> a.getAccountId().equals(targetAccountId));
        boolean guarded = lastAssignment && staffingWatched(session, Instant.now());

        if (guarded && !confirmLastAssignmentRemoval) {
            recordAudit(session.getId(), actor.accountId(), "SESSION_STAFF_REMOVED", "DENIED", correlationId,
                    "{\"removedAccountId\":" + targetAccountId
                            + ",\"code\":\"LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED\",\"status\":409}");
            throw new WorkshopLastStaffAssignmentException(
                    "This is the last Staff assignment of a session whose check-in has opened; confirm to remove it");
        }

        SessionStaffAssignment assignment = assignmentOpt.get();
        assignment.setActive(false);
        assignment.setRevokedAt(Instant.now());
        staffAssignmentRepository.save(assignment);

        recordAudit(session.getId(), actor.accountId(), "SESSION_STAFF_REMOVED", "SUCCESS", correlationId,
                "{\"removedAccountId\":" + targetAccountId
                        + (guarded ? ",\"lastAssignmentConfirmed\":true" : "") + "}");

        log.info("Removed Staff accountId={} from session reference={} by actorId={}",
                targetAccountId, sessionReference, actor.accountId());
    }

    /** True from {@code checkInOpensAt} while a PUBLISHED, SALES_PAUSED or IN_PROGRESS session has no active assignment. */
    public static boolean isMissingStaff(WorkshopSession session, boolean hasActiveAssignment, Instant now) {
        return !hasActiveAssignment && staffingWatched(session, now);
    }

    private static boolean staffingWatched(WorkshopSession session, Instant now) {
        return STAFFING_WATCHED_STATES.contains(session.getState())
                && session.getCheckInOpensAt() != null
                && !now.isBefore(session.getCheckInOpensAt());
    }

    private void validateManagerAuthority(AccountSummary actor) {
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
        List<Long> assigned = staffAssignmentRepository.findBySessionIdAndActiveTrue(session.getId()).stream()
                .map(SessionStaffAssignment::getAccountId).sorted().toList();

        return SessionResponse.builder()
                .id(session.getReference())
                .locationId(session.getLocation().getId())
                .locationName(session.getLocation().getDisplayName())
                .slotId(session.getSlot().getReference())
                .workshopDate(session.getWorkshopDate())
                .scheduledStartAt(session.getScheduledStartAt())
                .scheduledEndAt(session.getScheduledEndAt())
                .checkInOpensAt(session.getCheckInOpensAt())
                .checkInOpeningSource(session.getCheckInOpeningSource())
                .capacity(session.getCapacity())
                .state(session.getState())
                .assignedStaffAccountIds(assigned)
                .missingStaff(isMissingStaff(session, !assigned.isEmpty(), Instant.now()))
                .build();
    }
}
