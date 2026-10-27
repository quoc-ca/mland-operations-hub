package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.FacadeCommandIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.FacadeCommandIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.IdempotencyLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacadeCommandIdempotencyManager {

    private final FacadeCommandIdempotencyRecordRepository repository;
    private final PlatformTransactionManager transactionManager;

    @Value("${workshop.operations.idempotency.lease-duration}")
    private Duration leaseDuration;

    public enum ClaimStatus {
        ACQUIRED,
        ALREADY_COMPLETED,
        IN_PROGRESS,
        MISMATCH
    }

    @Getter
    public static class ClaimResult {
        private final ClaimStatus status;
        private final Long recordId;
        private final Long claimVersion;
        private final FacadeCommandIdempotencyRecord completedRecord;

        private ClaimResult(ClaimStatus status, Long recordId, Long claimVersion, FacadeCommandIdempotencyRecord completedRecord) {
            this.status = status;
            this.recordId = recordId;
            this.claimVersion = claimVersion;
            this.completedRecord = completedRecord;
        }

        public static ClaimResult acquired(Long recordId, Long claimVersion) {
            return new ClaimResult(ClaimStatus.ACQUIRED, recordId, claimVersion, null);
        }

        public static ClaimResult alreadyCompleted(FacadeCommandIdempotencyRecord record) {
            return new ClaimResult(ClaimStatus.ALREADY_COMPLETED, record.getId(), record.getClaimVersion(), record);
        }

        public static ClaimResult inProgress() {
            return new ClaimResult(ClaimStatus.IN_PROGRESS, null, null, null);
        }

        public static ClaimResult mismatch() {
            return new ClaimResult(ClaimStatus.MISMATCH, null, null, null);
        }
    }

    public ClaimResult acquireClaim(String facadeAction, String operationReference, String correlationReference, String semanticFingerprint) {
        Instant now = Instant.now();

        try {
            ClaimResult initialResult = executeInNewTransaction(() -> {
                Optional<FacadeCommandIdempotencyRecord> existingOpt = repository.findByFacadeActionAndOperationReference(facadeAction, operationReference);

                if (existingOpt.isPresent()) {
                    return evaluateExistingRecord(existingOpt.get(), semanticFingerprint, now);
                }

                FacadeCommandIdempotencyRecord newRecord = FacadeCommandIdempotencyRecord.builder()
                        .facadeAction(facadeAction)
                        .operationReference(operationReference)
                        .correlationReference(correlationReference)
                        .semanticFingerprint(semanticFingerprint)
                        .lifecycleStatus(IdempotencyLifecycleStatus.IN_PROGRESS)
                        .claimVersion(1L)
                        .leaseExpiresAt(now.plus(leaseDuration))
                        .claimedAt(now)
                        .build();

                newRecord = repository.saveAndFlush(newRecord);
                return ClaimResult.acquired(newRecord.getId(), 1L);
            });

            if (initialResult != null) {
                return initialResult;
            }
        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent insert race for {}:{}, rolling back failed child transaction", facadeAction, operationReference);
        }

        return executeInNewTransaction(() -> {
            Optional<FacadeCommandIdempotencyRecord> racedOpt = repository.findByFacadeActionAndOperationReference(facadeAction, operationReference);
            return racedOpt.map(r -> evaluateExistingRecord(r, semanticFingerprint, now))
                    .orElseGet(ClaimResult::inProgress);
        });
    }

    private <T> T executeInNewTransaction(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }

    private ClaimResult evaluateExistingRecord(FacadeCommandIdempotencyRecord record, String semanticFingerprint, Instant now) {
        if (!record.getSemanticFingerprint().equals(semanticFingerprint)) {
            log.warn("Idempotency violation: reused opRef={} with mismatched fingerprint", record.getOperationReference());
            return ClaimResult.mismatch();
        }

        if (record.getLifecycleStatus() == IdempotencyLifecycleStatus.COMPLETED) {
            log.info("Returning completed outcome for opRef={}", record.getOperationReference());
            return ClaimResult.alreadyCompleted(record);
        }

        // Status is IN_PROGRESS
        if (record.getLeaseExpiresAt().isAfter(now)) {
            log.info("Concurrent equivalent in-progress for opRef={}", record.getOperationReference());
            return ClaimResult.inProgress();
        }

        // Lease expired; attempt reclaim
        Instant newLeaseExpiresAt = now.plus(leaseDuration);
        int updated = repository.reclaimExpiredLease(
            record.getId(),
            record.getClaimVersion(),
            IdempotencyLifecycleStatus.IN_PROGRESS,
            now,
            newLeaseExpiresAt,
            now
        );

        if (updated == 1) {
            long newVersion = record.getClaimVersion() + 1;
            log.info("Reclaimed expired lease for opRef={}, new claimVersion={}", record.getOperationReference(), newVersion);
            return ClaimResult.acquired(record.getId(), newVersion);
        } else {
            log.info("Reclaim race lost for opRef={}", record.getOperationReference());
            return ClaimResult.inProgress();
        }
    }

    @Transactional
    public void completeClaim(Long recordId, Long claimVersion, OutcomeStatus status, RejectionReason reason, String responsePayload) {
        String reasonStr = reason != null ? reason.name() : null;
        int updated = repository.completeClaim(
            recordId,
            claimVersion,
            IdempotencyLifecycleStatus.COMPLETED,
            status,
            reasonStr,
            responsePayload,
            Instant.now()
        );

        if (updated == 0) {
            throw new StaleClaimantException("Stale claimant: claimVersion " + claimVersion + " is no longer valid for recordId " + recordId);
        }
    }
}
