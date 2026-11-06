package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReferenceImageStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.TemporaryImageReference;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.AiTextInteractionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.TemporaryImageReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataRetentionCleanupJob {

    private final AiTextInteractionRepository aiTextInteractionRepository;
    private final TemporaryImageReferenceRepository temporaryImageRepository;
    private final ReferenceImageStoragePort referenceImageStoragePort;

    /**
     * Executes daily at 02:00 AM by default to enforce 30-day retention for AI text interactions
     * and zero-retention / temporary image file deletion according to spec.md data retention invariants.
     */
    @Scheduled(cron = "${mland.retention.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void runRetentionCleanup() {
        log.info("Starting scheduled data retention cleanup job...");
        int purgedTextCount = cleanupExpiredTextInteractions(Instant.now());
        int purgedImageCount = cleanupDanglingTemporaryImages();
        log.info("Scheduled data retention cleanup finished: purged {} expired text interactions, {} temporary image records.",
                purgedTextCount, purgedImageCount);
    }

    @Transactional
    public int cleanupExpiredTextInteractions(Instant cutoffTime) {
        Instant effectiveCutoff = cutoffTime != null ? cutoffTime : Instant.now();
        int deletedCount = aiTextInteractionRepository.deleteExpiredInteractions(effectiveCutoff);
        if (deletedCount > 0) {
            log.info("Data retention: Purged {} expired AI text interactions older than 30-day retention window (cutoff={})",
                    deletedCount, effectiveCutoff);
        }
        return deletedCount;
    }

    @Transactional
    public int cleanupDanglingTemporaryImages() {
        List<TemporaryImageReference> pendingList = temporaryImageRepository.findByDeletedFalse();
        int cleanedCount = 0;
        for (TemporaryImageReference ref : pendingList) {
            try {
                if (ref.getStorageObjectKey() != null) {
                    referenceImageStoragePort.deleteTemporary(ref.getStorageObjectKey());
                }
                ref.setDeleted(true);
                ref.setDeletedAt(Instant.now());
                ref.setDeletionOutcome("RETENTION_CLEANUP_JOB");
                temporaryImageRepository.save(ref);
                cleanedCount++;
            } catch (Exception e) {
                log.warn("Failed to purge temporary image file key={}: {}", ref.getStorageObjectKey(), e.getMessage());
            }
        }
        return cleanedCount;
    }
}
