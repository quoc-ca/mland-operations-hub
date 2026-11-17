package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewIdempotencyRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewIdempotencyService {

    private final ReviewIdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";

    public String computeHash(Object payload) {
        if (payload == null) {
            return "empty";
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            log.warn("Failed to compute payload hash, fallback to string hash", ex);
            return String.valueOf(payload.hashCode());
        }
    }

    public <T> Optional<T> findCompletedResponse(String idempotencyKey, String actorScope, Class<T> responseClass) {
        return findCompletedResponse(idempotencyKey, actorScope, null, responseClass);
    }

    public <T> Optional<T> findCompletedResponse(String idempotencyKey, String actorScope,
                                                 String requestHash, Class<T> responseClass) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        Optional<ReviewIdempotencyRecord> opt = repository.findByIdempotencyKeyAndActorScope(idempotencyKey.trim(), actorScope);
        if (opt.isPresent()) {
            ReviewIdempotencyRecord record = opt.get();
            if (record.isExpired()) {
                return Optional.empty();
            }
            if (requestHash != null && !requestHash.equals(record.getRequestHash())) {
                throw ReviewManagementException.conflict("REVIEW_IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key đã được dùng cho payload khác.");
            }
            if (STATUS_IN_PROGRESS.equalsIgnoreCase(record.getStatus())) {
                throw ReviewManagementException.conflict("REVIEW_IDEMPOTENCY_CONFLICT", "Yêu cầu với Idempotency Key này đang được xử lý đồng thời.");
            }
            if (STATUS_COMPLETED.equalsIgnoreCase(record.getStatus()) && record.getResponseBody() != null) {
                try {
                    return Optional.of(objectMapper.readValue(record.getResponseBody(), responseClass));
                } catch (Exception ex) {
                    log.error("Failed to deserialize idempotency response body", ex);
                }
            }
        }
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPending(String idempotencyKey, String commandType, String actorScope, String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        ReviewIdempotencyRecord record = ReviewIdempotencyRecord.builder()
                .id(UUID.randomUUID().toString())
                .idempotencyKey(idempotencyKey.trim())
                .commandType(commandType)
                .actorScope(actorScope)
                .requestHash(requestHash)
                .status(STATUS_IN_PROGRESS)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        try {
            repository.save(record);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw ReviewManagementException.conflict("REVIEW_IDEMPOTENCY_CONFLICT",
                    "Yêu cầu cùng Idempotency-Key đang được xử lý.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String idempotencyKey, String actorScope, Object responsePayload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        repository.findByIdempotencyKeyAndActorScope(idempotencyKey.trim(), actorScope).ifPresent(record -> {
            try {
                String json = responsePayload != null ? objectMapper.writeValueAsString(responsePayload) : null;
                record.setResponseBody(json);
                record.setStatus(STATUS_COMPLETED);
                repository.save(record);
            } catch (Exception ex) {
                log.error("Failed to serialize idempotency success response", ex);
            }
        });
    }
}

