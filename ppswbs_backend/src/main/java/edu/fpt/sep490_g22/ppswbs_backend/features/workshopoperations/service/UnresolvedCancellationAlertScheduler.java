package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Finds sessions still {@code CANCELLATION_REQUESTED} 24 hours after their scheduled end and
 * alerts the Managers once per session (FR-010a, T133). Not transactional: each session runs in
 * its own transaction ({@link UnresolvedCancellationAlertTransition}); a failure is logged as WARN
 * and the next scan retries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnresolvedCancellationAlertScheduler {

    private final WorkshopSessionRepository sessionRepository;
    private final UnresolvedCancellationAlertTransition alertTransition;

    @Scheduled(fixedDelayString = "${workshop.operations.scheduler.fixed-delay-ms:10000}")
    public void alertUnresolvedCancellations() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(UnresolvedCancellationAlertTransition.ALERT_AFTER);
        for (String reference : sessionRepository
                .findByStateAndScheduledEndAtLessThanEqual(SessionState.CANCELLATION_REQUESTED, cutoff)
                .stream().map(WorkshopSession::getReference).toList()) {
            try {
                alertTransition.alertIfStillDue(reference, now);
            } catch (RuntimeException e) {
                log.warn("Cancellation alert failed for workshop session reference={}; retrying on the next scan",
                        reference, e);
            }
        }
    }
}
