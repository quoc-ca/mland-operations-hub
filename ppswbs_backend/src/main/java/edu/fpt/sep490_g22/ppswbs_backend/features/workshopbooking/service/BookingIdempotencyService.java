package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingIdempotencyLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingIdempotencyRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
@Slf4j
public class BookingIdempotencyService {

    private final BookingIdempotencyRecordRepository repository;
    private final TransactionTemplate requiresNewTemplate;
    private final Duration leaseDuration;

    public BookingIdempotencyService(BookingIdempotencyRecordRepository repository,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${workshop.booking.idempotency.lease-duration:PT30S}") Duration leaseDuration) {
        this.repository = repository;
        this.leaseDuration = leaseDuration;
        this.requiresNewTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Compute SHA-256 semantic fingerprint of the normalized request content.
     */
    public String computeFingerprint(String semanticContent) {
        if (semanticContent == null) {
            semanticContent = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(semanticContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    /**
     * Atomically acquire a claim on an idempotency key for a specific command action.
     *
     * @param idempotencyKey       the unique client-provided idempotency key
     * @param commandAction        the action name (e.g., CREATE_BOOKING, START_PAYMENT)
     * @param semanticContent      the stringified semantic payload to fingerprint
     * @param correlationReference distributed tracing correlation id
     * @return BookingIdempotencyClaim indicating either cached outcome or live claim with fencing version
     */
    public BookingIdempotencyClaim acquireClaim(String idempotencyKey,
                                                String commandAction,
                                                String semanticContent,
                                                String correlationReference) {
        String fingerprint = computeFingerprint(semanticContent);

        try {
            return requiresNewTemplate.execute(status -> tryAcquireOrEvaluate(idempotencyKey, commandAction, fingerprint, correlationReference));
        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent insert race for key={}, evaluating winner in clean transaction", idempotencyKey);
            return requiresNewTemplate.execute(status -> evaluateExisting(idempotencyKey, commandAction, fingerprint, correlationReference));
        }
    }

    private BookingIdempotencyClaim tryAcquireOrEvaluate(String key, String action, String fingerprint, String correlationRef) {
        Optional<BookingIdempotencyRecord> opt = repository.findByIdempotencyKeyAndCommandAction(key, action);
        if (opt.isEmpty()) {
            Instant now = Instant.now();
            BookingIdempotencyRecord record = BookingIdempotencyRecord.builder()
                    .idempotencyKey(key)
                    .commandAction(action)
                    .correlationReference(correlationRef != null ? correlationRef : "UNKNOWN")
                    .semanticFingerprint(fingerprint)
                    .lifecycleStatus(BookingIdempotencyLifecycleStatus.IN_PROGRESS)
                    .claimVersion(1L)
                    .leaseExpiresAt(now.plus(leaseDuration))
                    .claimedAt(now)
                    .build();
            BookingIdempotencyRecord saved = repository.saveAndFlush(record);
            return BookingIdempotencyClaim.live(saved.getId(), saved.getClaimVersion());
        }

        return evaluateRecord(opt.get(), fingerprint, correlationRef);
    }

    private BookingIdempotencyClaim evaluateExisting(String key, String action, String fingerprint, String correlationRef) {
        BookingIdempotencyRecord record = repository.findByIdempotencyKeyAndCommandAction(key, action)
                .orElseThrow(() -> new IllegalStateException("Expected idempotency record not found after race resolution"));
        return evaluateRecord(record, fingerprint, correlationRef);
    }

    private BookingIdempotencyClaim evaluateRecord(BookingIdempotencyRecord record, String fingerprint, String correlationRef) {
        if (!record.getSemanticFingerprint().equals(fingerprint)) {
            log.warn("Idempotency conflict: key={} was previously used with different semantic fingerprint", record.getIdempotencyKey());
            throw new BookingIdempotencyConflictException("Idempotency key was previously used with different request parameters");
        }

        if (record.getLifecycleStatus() == BookingIdempotencyLifecycleStatus.COMPLETED) {
            log.info("Returning completed cached outcome for key={}, action={}", record.getIdempotencyKey(), record.getCommandAction());
            return BookingIdempotencyClaim.cached(record.getStatusCode(), record.getResponsePayload());
        }

        Instant now = Instant.now();
        if (record.getLeaseExpiresAt().isBefore(now)) {
            log.warn("Reclaiming expired lease for key={}, action={}, previous claimant timed out",
                    record.getIdempotencyKey(), record.getCommandAction());
            Instant newExpiry = now.plus(leaseDuration);
            int updated = repository.reclaimExpiredLease(record.getId(), record.getClaimVersion(), newExpiry, correlationRef, now);
            if (updated > 0) {
                return BookingIdempotencyClaim.live(record.getId(), record.getClaimVersion() + 1);
            }
        }

        log.warn("Concurrent command execution in progress for key={}, action={}", record.getIdempotencyKey(), record.getCommandAction());
        throw new BookingIdempotencyConflictException("A command with this Idempotency-Key is currently being processed");
    }

    /**
     * Complete the acquired claim with the HTTP status and serialized response payload.
     * Enforces claim-version fencing.
     */
    public void completeClaim(Long recordId, Long expectedVersion, int statusCode, String responsePayload) {
        requiresNewTemplate.executeWithoutResult(status -> {
            Instant now = Instant.now();
            int updated = repository.completeClaim(recordId, expectedVersion,
                    BookingIdempotencyLifecycleStatus.COMPLETED, now, statusCode, responsePayload, now);
            if (updated == 0) {
                log.error("Fencing check failed for claimId={}, version={}. Another claimant may have reclaimed lease",
                        recordId, expectedVersion);
                throw new IllegalStateException("Fencing check failed: claim was superseded by another claimant");
            }
        });
    }

    /** Completes a claim in the caller's business transaction so booking and cached outcome commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeClaimWithinTransaction(Long recordId, Long expectedVersion, int statusCode, String responsePayload) {
        Instant now = Instant.now();
        int updated = repository.completeClaim(recordId, expectedVersion,
                BookingIdempotencyLifecycleStatus.COMPLETED, now, statusCode, responsePayload, now);
        if (updated == 0) {
            throw new IllegalStateException("Booking idempotency claim was superseded before completion");
        }
    }
}
