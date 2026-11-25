package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;
import java.util.Optional;

/**
 * Starts one due session in its own transaction. A separate bean (not a method of the
 * scheduler) so the call goes through the Spring proxy and {@code REQUIRES_NEW} applies:
 * one failing session rolls back only itself, never the rest of the batch (FR-010d, T125).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkshopSessionStartTransition {

    private final WorkshopSessionRepository sessionRepository;
    private final OperationsAuditEventRepository auditEventRepository;

    /** What was started: the state it left and its planned start, for lateness monitoring. */
    public record Started(String reference, SessionState fromState, Instant scheduledStartAt) {
    }

    /**
     * Moves a {@code PUBLISHED} or {@code SALES_PAUSED} session to {@code IN_PROGRESS} when it
     * is still due. Empty when the session changed meanwhile (another actor started, paused
     * or cancelled it) and nothing was written.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Started> startIfStillDue(String reference, Instant now) {
        WorkshopSession session = sessionRepository.findByReferenceWithLock(reference).orElse(null);
        if (session == null
                || (session.getState() != SessionState.PUBLISHED && session.getState() != SessionState.SALES_PAUSED)
                || session.getScheduledStartAt() == null
                || session.getScheduledStartAt().isAfter(now)) {
            return Optional.empty();
        }

        SessionState fromState = session.getState();
        session.setState(SessionState.IN_PROGRESS);
        sessionRepository.save(session);
        auditEventRepository.save(OperationsAuditEvent.builder()
                .sessionId(session.getId())
                .actorId(null)
                .action("SESSION_AUTO_STARTED")
                .outcome("SUCCESS")
                .correlationId("scheduler-" + UUID.randomUUID())
                .metadata("{\"fromState\":\"" + fromState.name() + "\",\"toState\":\"IN_PROGRESS\"}")
                .build());
        log.info("Automatically started workshop session reference={} fromState={}", reference, fromState);
        return Optional.of(new Started(reference, fromState, session.getScheduledStartAt()));
    }
}
