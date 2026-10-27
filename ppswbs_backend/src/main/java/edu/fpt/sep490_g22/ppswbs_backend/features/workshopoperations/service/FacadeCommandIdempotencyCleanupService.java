package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.FacadeCommandIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.IdempotencyLifecycleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacadeCommandIdempotencyCleanupService {

    private final FacadeCommandIdempotencyRecordRepository repository;

    @Value("${workshop.operations.idempotency.retention-duration:PT24H}")
    private Duration retentionDuration;

    @Transactional
    public int cleanupExpiredCompletedRecords() {
        Duration effectiveRetention = retentionDuration;
        if (effectiveRetention == null || effectiveRetention.compareTo(Duration.ofHours(24)) < 0) {
            log.warn("Configured retention duration {} is below minimum 24 hours; enforcing 24 hours", effectiveRetention);
            effectiveRetention = Duration.ofHours(24);
        }

        Instant cutoff = Instant.now().minus(effectiveRetention);
        int deletedCount = repository.deleteCompletedBefore(IdempotencyLifecycleStatus.COMPLETED, cutoff);
        log.info("Cleaned up {} completed idempotency records older than {}", deletedCount, cutoff);
        return deletedCount;
    }
}
