package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Finalizes sessions left `IN_PROGRESS` once their scheduled end is the configured delay in the past
 * (003.2 FR-059). Off unless switched on: the first run closes every session already left running.
 * Deliberately not transactional: each session has its own transaction, so one failure does not stop the others.
 */
@Component
@Slf4j
public class WorkshopSessionAutoFinalizeScheduler {

    private final WorkshopSessionRepository sessionRepository;
    private final AttendanceFinalizationService finalizationService;
    private final boolean enabled;
    private final Duration delay;

    public WorkshopSessionAutoFinalizeScheduler(WorkshopSessionRepository sessionRepository,
                                                AttendanceFinalizationService finalizationService,
                                                @Value("${workshop.operations.auto-finalize.enabled:false}") boolean enabled,
                                                @Value("${workshop.operations.auto-finalize.delay:PT2H}") Duration delay) {
        this.sessionRepository = sessionRepository;
        this.finalizationService = finalizationService;
        this.enabled = enabled;
        this.delay = delay;
    }

    @Scheduled(fixedDelayString = "${workshop.operations.scheduler.fixed-delay-ms:10000}")
    public void finalizeOverdueSessions() {
        if (!enabled) {
            return;
        }
        Instant endedBy = Instant.now().minus(delay);
        for (String reference : sessionRepository.findByStateAndScheduledEndAtLessThanEqual(SessionState.IN_PROGRESS, endedBy)
                .stream().map(WorkshopSession::getReference).toList()) {
            try {
                finalizationService.finalizeBySystemIfStillDue(reference, endedBy);
            } catch (RuntimeException e) {
                log.error("Automatic finalization failed for workshop session reference={}; the session is retried on the next run",
                        reference, e);
            }
        }
    }
}
