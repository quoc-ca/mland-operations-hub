package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.RenditionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TombstoneRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Tombstone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionService {

    private final NotificationRequestRepository notificationRequestRepository;
    private final RenditionRepository renditionRepository;
    private final TombstoneRepository tombstoneRepository;

    /**
     * Daily retention cleanup sweep at 02:00 AM (FR-025, FR-026).
     */
    @Scheduled(cron = "${app.notification.retention.cron:0 0 2 * * ?}")
    @Transactional
    public void scheduledSweep() {
        sweepExpiredData(90);
    }

    /**
     * Executes the sweep for requests exceeding the retention threshold in days.
     */
    @Transactional
    public int sweepExpiredData(long retentionDays) {
        Instant now = Instant.now();
        Instant threshold = now.minus(retentionDays, ChronoUnit.DAYS);

        // 1. Scrub expired credentials
        List<NotificationRequest> activeWithCredentials = notificationRequestRepository
                .findAll((root, query, cb) -> cb.and(
                        cb.isNotNull(root.get("credentialExpiresAt")),
                        cb.lessThanOrEqualTo(root.get("credentialExpiresAt"), now),
                        cb.isNotNull(root.get("credentialReference"))
                ));

        for (NotificationRequest req : activeWithCredentials) {
            req.setCredentialReference(null);
            notificationRequestRepository.save(req);
            log.info("Retention: scrubbed expired credential for notification request {}", req.getId());
        }

        // 2. Scrub requests older than 90 days and create tombstones
        List<NotificationRequest> expiredRequests = notificationRequestRepository
                .findByTerminalAtBeforeAndDeliveryStateIn(threshold, List.of("SENT", "FAILED", "CANCELLED"));

        int scrubbedCount = 0;
        for (NotificationRequest req : expiredRequests) {
            Optional<Tombstone> existingTombstone = tombstoneRepository.findByOriginalRequestId(req.getId());
            if (existingTombstone.isEmpty()) {
                Tombstone tombstone = new Tombstone();
                tombstone.setOriginalRequestId(req.getId());
                tombstone.setBusinessEventReference(req.getBusinessEventReference());
                tombstone.setTemplateCode(req.getTemplateCode());
                tombstone.setTerminalState(req.getDeliveryState());
                tombstone.setAttemptCount(req.getAttemptCount());
                tombstone.setRequestCreatedAt(req.getCreatedAt());
                tombstone.setRequestTerminalAt(req.getTerminalAt() != null ? req.getTerminalAt() : now);
                tombstone.setScrubbedAt(now);
                tombstoneRepository.save(tombstone);
            }

            // Scrub sensitive payload and rendition
            req.setRecipientSnapshot("[SCRUBBED]");
            req.setBusinessDataSnapshot("[SCRUBBED]");
            req.setCredentialReference(null);
            notificationRequestRepository.save(req);

            renditionRepository.findByNotificationRequestId(req.getId())
                    .ifPresent(renditionRepository::delete);

            scrubbedCount++;
            log.info("Retention: scrubbed request {} into tombstone", req.getId());
        }

        return scrubbedCount;
    }
}

