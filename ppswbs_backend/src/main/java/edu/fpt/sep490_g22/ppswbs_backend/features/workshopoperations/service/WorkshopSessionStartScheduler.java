package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Opens check-in at the configured opening instant while leaving the session
 * PUBLISHED, then starts the session at its scheduled start instant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkshopSessionStartScheduler {

    private final WorkshopSessionRepository sessionRepository;
    private final OperationsAuditEventRepository auditEventRepository;

    @Scheduled(fixedDelayString = "${workshop.operations.scheduler.fixed-delay-ms:10000}")
    @Transactional
    public void transitionDueSessions() {
        Instant now = Instant.now();
        sessionRepository.findByStateAndScheduledStartAtLessThanEqual(SessionState.PUBLISHED, now)
                .forEach(candidate -> transitionIfStillDue(candidate.getReference(), now));
    }

    @Transactional
    protected void transitionIfStillDue(String reference, Instant now) {
        WorkshopSession session = sessionRepository.findByReferenceWithLock(reference).orElse(null);
        if (session == null || session.getState() != SessionState.PUBLISHED
                || session.getScheduledStartAt() == null
                || session.getScheduledStartAt().isAfter(now)) {
            return;
        }

        session.setState(SessionState.IN_PROGRESS);
        sessionRepository.save(session);
        auditEventRepository.save(OperationsAuditEvent.builder()
                .sessionId(session.getId())
                .actorId(null)
                .action("SESSION_AUTO_STARTED")
                .outcome("SUCCESS")
                .correlationId("scheduler-" + UUID.randomUUID())
                .metadata("{\"fromState\":\"PUBLISHED\",\"toState\":\"IN_PROGRESS\"}")
                .build());
        log.info("Automatically started workshop session reference={}", reference);
    }
}
