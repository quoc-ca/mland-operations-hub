package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementIdempotencyOutcomeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementIdempotencyOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class SettlementIdempotencyService {
    private final SettlementIdempotencyOutcomeRepository repository;

    @Transactional(readOnly = true)
    public Optional<String> findOutcomeReference(String operationReference, String requestFingerprint) {
        return repository.findByOperationReference(operationReference).map(outcome -> {
            if (!requestFingerprint.equals(outcome.getRequestFingerprint())) {
                throw new WorkshopSettlementConflictException("IDEMPOTENCY_CONFLICT");
            }
            if (outcome.getOutcomeReference() == null || outcome.getOutcomeReference().isBlank()) {
                throw new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE");
            }
            return outcome.getOutcomeReference();
        });
    }

    @Transactional
    public SettlementIdempotencyResult execute(String operationReference, String requestFingerprint,
                                               Supplier<String> outcomeReferenceSupplier) {
        require(operationReference, "operationReference");
        require(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(outcomeReferenceSupplier, "outcomeReferenceSupplier");

        var existing = repository.findByOperationReference(operationReference);
        if (existing.isPresent()) {
            SettlementIdempotencyOutcome outcome = existing.get();
            if (!requestFingerprint.equals(outcome.getRequestFingerprint())) {
                throw new WorkshopSettlementConflictException("IDEMPOTENCY_CONFLICT");
            }
            if (outcome.getOutcomeReference() == null || outcome.getOutcomeReference().isBlank()) {
                throw new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE");
            }
            return new SettlementIdempotencyResult(true, outcome.getOutcomeReference());
        }

        String outcomeReference = outcomeReferenceSupplier.get();
        require(outcomeReference, "outcomeReference");
        repository.saveAndFlush(SettlementIdempotencyOutcome.builder()
                .operationReference(operationReference)
                .requestFingerprint(requestFingerprint)
                .outcomeStatus("COMPLETED")
                .outcomeReference(outcomeReference)
                .correlationReference(operationReference)
                .createdAt(java.time.Instant.now())
                .completedAt(java.time.Instant.now())
                .build());
        return new SettlementIdempotencyResult(false, outcomeReference);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
