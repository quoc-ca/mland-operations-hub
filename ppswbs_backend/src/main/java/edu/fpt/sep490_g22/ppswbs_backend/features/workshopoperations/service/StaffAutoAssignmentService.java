package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.InternalRosterEntry;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.RosterOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionStaffAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owner decision 2026-11-16: an active Staff member is assigned by default to every session of the location in
 * their scope. The assignment is still a real {@code session_staff_assignments} row, so a Manager can revoke it for
 * one session and the 2026-11-06 session-assignment authorization rule keeps its meaning.
 * <p>
 * A session that already has a row for the Staff member, active or revoked, is left alone: a revocation by a Manager
 * is never undone by this service. It runs when a session is created and periodically, so a Staff member added to
 * a location or activated later also gets the sessions that already exist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffAutoAssignmentService {

    static final String ACTION = "SESSION_STAFF_AUTO_ASSIGNED";
    private static final ZoneId WORKSHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** The states in which Staff may be assigned (same as a Manager assignment, T130). */
    private static final Set<SessionState> ASSIGNABLE_STATES = EnumSet.of(
            SessionState.DRAFT, SessionState.PUBLISHED, SessionState.SALES_PAUSED, SessionState.IN_PROGRESS);

    private final AccountFacade accountFacade;
    private final WorkshopSessionRepository sessionRepository;
    private final SessionStaffAssignmentRepository staffAssignmentRepository;
    private final OperationsAuditEventRepository auditEventRepository;

    /** Assigns the location's eligible Staff to a session that was just created. Returns how many were added. */
    @Transactional
    public int assignRosterToNewSession(WorkshopSession session, Long actorAccountId) {
        if (session == null || session.getLocation() == null || !ASSIGNABLE_STATES.contains(session.getState())) {
            return 0;
        }
        return assign(session, rosterOf(session.getLocation().getId()), Set.of(), actorAccountId);
    }

    /** Gives every eligible Staff member the upcoming sessions of their locations that they do not have yet. */
    @Transactional
    public int syncUpcomingSessions() {
        LocalDate today = Instant.now().atZone(WORKSHOP_ZONE).toLocalDate();
        List<WorkshopSession> sessions = sessionRepository.findByWorkshopDateGreaterThanEqualAndStateIn(today, ASSIGNABLE_STATES);
        if (sessions.isEmpty()) {
            return 0;
        }
        Map<Long, Set<Long>> knownBySession = new HashMap<>();
        for (SessionStaffAssignment row : staffAssignmentRepository.findBySessionIdIn(
                sessions.stream().map(WorkshopSession::getId).toList())) {
            knownBySession.computeIfAbsent(row.getSession().getId(), key -> new HashSet<>()).add(row.getAccountId());
        }
        Map<Long, List<Long>> rosters = new HashMap<>();
        int added = 0;
        for (WorkshopSession session : sessions) {
            if (session.getLocation() == null) {
                continue;
            }
            List<Long> roster = rosters.computeIfAbsent(session.getLocation().getId(), this::rosterOf);
            added += assign(session, roster, knownBySession.getOrDefault(session.getId(), Set.of()), null);
        }
        if (added > 0) {
            log.info("Auto-assigned Staff to upcoming sessions: {} new assignment(s)", added);
        }
        return added;
    }

    @Scheduled(initialDelayString = "${workshop.staff-auto-assign.initial-delay-ms:60000}",
            fixedDelayString = "${workshop.staff-auto-assign.delay-ms:300000}")
    public void scheduledSync() {
        try {
            syncUpcomingSessions();
        } catch (RuntimeException e) {
            log.warn("Staff auto-assignment sync failed: {}", e.getMessage());
        }
    }

    private int assign(WorkshopSession session, List<Long> roster, Set<Long> alreadyKnown, Long actorAccountId) {
        Set<Long> known = new HashSet<>(alreadyKnown);
        if (alreadyKnown.isEmpty()) {
            known.addAll(staffAssignmentRepository.findBySessionIdIn(List.of(session.getId())).stream()
                    .map(SessionStaffAssignment::getAccountId).collect(Collectors.toSet()));
        }
        int added = 0;
        for (Long accountId : roster) {
            if (!known.add(accountId)) {
                continue; // a row exists: active, or revoked by a Manager, which must stay revoked
            }
            staffAssignmentRepository.save(SessionStaffAssignment.builder()
                    .session(session)
                    .accountId(accountId)
                    .assignedById(actorAccountId != null ? actorAccountId : accountId)
                    .active(true)
                    .assignedAt(Instant.now())
                    .build());
            audit(session.getId(), actorAccountId, accountId);
            added++;
        }
        return added;
    }

    private List<Long> rosterOf(Long locationId) {
        RosterOutcome outcome = accountFacade.findEligibleRoster(locationId);
        if (outcome instanceof RosterOutcome.Available available) {
            return available.entries().stream().map(InternalRosterEntry::accountId)
                    .map(id -> id.value()).distinct().toList();
        }
        return List.of();
    }

    private void audit(Long sessionId, Long actorId, Long assignedAccountId) {
        try {
            auditEventRepository.save(OperationsAuditEvent.builder()
                    .sessionId(sessionId)
                    .actorId(actorId)
                    .action(ACTION)
                    .outcome("SUCCESS")
                    .correlationId(UUID.randomUUID().toString())
                    .metadata("{\"assignedAccountId\":" + assignedAccountId + "}")
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to record audit event: action={}, error={}", ACTION, e.getMessage());
        }
    }
}
