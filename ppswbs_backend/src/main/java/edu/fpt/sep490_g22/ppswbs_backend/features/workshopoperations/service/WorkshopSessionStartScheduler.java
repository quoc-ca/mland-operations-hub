package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Starts due sessions at their scheduled start instant. Check-in opens earlier and leaves
 * the session {@code PUBLISHED}/{@code SALES_PAUSED}. Each session is started in its own
 * transaction ({@link WorkshopSessionStartTransition}); this method is deliberately not
 * transactional so a failing session cannot roll back the others (FR-010d, T125).
 *
 * <p>A start later than the threshold (default 5 minutes) after {@code scheduledStartAt} is a
 * monitored failure: an {@code ERROR} log and the {@value #LATE_START_METRIC} counter, no email
 * (FR-010d, F14, T119). The scheduler still starts the session whenever it runs again.</p>
 */
@Component
@Slf4j
public class WorkshopSessionStartScheduler {

    public static final String LATE_START_METRIC = "workshop.session.auto_start.late";

    private static final List<SessionState> STARTABLE = List.of(SessionState.PUBLISHED, SessionState.SALES_PAUSED);

    private final WorkshopSessionRepository sessionRepository;
    private final WorkshopSessionStartTransition startTransition;
    private final MeterRegistry meterRegistry;
    private final Duration lateStartThreshold;

    public WorkshopSessionStartScheduler(WorkshopSessionRepository sessionRepository,
                                         WorkshopSessionStartTransition startTransition,
                                         MeterRegistry meterRegistry,
                                         @Value("${workshop.operations.scheduler.late-start-threshold:PT5M}") Duration lateStartThreshold) {
        this.sessionRepository = sessionRepository;
        this.startTransition = startTransition;
        this.meterRegistry = meterRegistry;
        this.lateStartThreshold = lateStartThreshold;
    }

    @Scheduled(fixedDelayString = "${workshop.operations.scheduler.fixed-delay-ms:10000}")
    public void transitionDueSessions() {
        Instant now = Instant.now();
        for (String reference : sessionRepository.findByStateInAndScheduledStartAtLessThanEqual(STARTABLE, now)
                .stream().map(s -> s.getReference()).toList()) {
            try {
                startTransition.startIfStillDue(reference, now).ifPresent(started -> reportIfLate(started, now));
            } catch (RuntimeException e) {
                log.error("Automatic start failed for workshop session reference={}; the session is retried on the next run",
                        reference, e);
            }
        }
    }

    private void reportIfLate(WorkshopSessionStartTransition.Started started, Instant now) {
        Duration lateness = Duration.between(started.scheduledStartAt(), now);
        if (lateness.compareTo(lateStartThreshold) > 0) {
            log.error("Workshop session reference={} auto-started {} minutes after its scheduled start (threshold {} minutes)",
                    started.reference(), lateness.toMinutes(), lateStartThreshold.toMinutes());
            meterRegistry.counter(LATE_START_METRIC).increment();
        }
    }
}
