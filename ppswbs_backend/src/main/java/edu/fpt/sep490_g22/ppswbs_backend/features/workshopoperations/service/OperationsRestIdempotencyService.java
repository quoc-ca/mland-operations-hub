package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.FacadeCommandIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.FacadeCommandIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;

/** Durable REST command deduplication for Manager Operations writes. */
@Service
@RequiredArgsConstructor
public class OperationsRestIdempotencyService {
    private final FacadeCommandIdempotencyManager manager;
    private final FacadeCommandIdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public <T> T execute(String idempotencyKey, String action, String scope, Object command,
                         Class<T> responseType, Supplier<T> commandAction) {
        String key = requiredKey(idempotencyKey);
        String fingerprint = fingerprint(action, scope, command);
        FacadeCommandIdempotencyRecord existing = repository.findFirstByOperationReference(key).orElse(null);
        if (existing != null && !existing.getFacadeAction().equals(action)) {
            if (!fingerprint.equals(existing.getSemanticFingerprint())) {
                throw new WorkshopIdempotencyConflictException("IDEMPOTENCY_CONFLICT");
            }
            if (existing.getLifecycleStatus() == edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.IdempotencyLifecycleStatus.IN_PROGRESS) {
                throw new WorkshopIdempotencyInProgressException("IDEMPOTENCY_IN_PROGRESS");
            }
            return readResponse(existing, responseType);
        }
        ClaimResult claim = manager.acquireClaim(action, key, scope, fingerprint);
        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            throw new WorkshopIdempotencyConflictException("IDEMPOTENCY_CONFLICT");
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            throw new WorkshopIdempotencyInProgressException("IDEMPOTENCY_IN_PROGRESS");
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return readResponse(claim.getCompletedRecord(), responseType);
        }
        try {
            T response = commandAction.get();
            manager.completeClaim(claim.getRecordId(), claim.getClaimVersion(),
                    edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus.ACCEPTED,
                    null, writeResponse(response));
            return response;
        } catch (RuntimeException ex) {
            manager.abandonClaim(claim.getRecordId(), claim.getClaimVersion());
            throw ex;
        }
    }

    private <T> T readResponse(FacadeCommandIdempotencyRecord record, Class<T> responseType) {
        if (responseType == Void.class || record.getResponsePayload() == null) return null;
        try { return objectMapper.readValue(record.getResponsePayload(), responseType); }
        catch (Exception ex) { throw new IllegalStateException("IDEMPOTENCY_OUTCOME_UNREADABLE", ex); }
    }

    private String writeResponse(Object response) {
        if (response == null) return null;
        try { return objectMapper.writeValueAsString(response); }
        catch (Exception ex) { throw new IllegalStateException("IDEMPOTENCY_OUTCOME_UNSERIALIZABLE", ex); }
    }

    private String requiredKey(String key) {
        if (key == null || key.isBlank()) throw new WorkshopValidationException("Idempotency-Key is required");
        if (key.length() > 128) throw new WorkshopValidationException("Idempotency-Key must not exceed 128 characters");
        return key.trim();
    }

    private String fingerprint(String action, String scope, Object command) {
        try {
            String payload = action + "|" + scope + "|" + objectMapper.writeValueAsString(command);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Unable to fingerprint idempotent command", ex); }
    }
}
