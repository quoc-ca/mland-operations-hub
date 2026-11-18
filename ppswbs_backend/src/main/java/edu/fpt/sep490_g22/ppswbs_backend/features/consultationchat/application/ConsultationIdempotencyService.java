package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure.ConsultationIdempotencyRecordRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationIdempotencyService {

    private final ConsultationIdempotencyRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<ConsultationIdempotencyRecord> checkReplay(
            String idempotencyKey, String commandType, String actorScope, Object requestPayload
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        Optional<ConsultationIdempotencyRecord> existing = recordRepository.findByIdempotencyKeyAndActorScope(
                idempotencyKey.trim(), actorScope
        );

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        ConsultationIdempotencyRecord record = existing.get();
        String currentHash = computeHash(requestPayload);
        if (!currentHash.equals(record.getRequestHash())) {
            throw ConsultationException.conflict(
                    "CHAT_IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key đã được sử dụng với nội dung yêu cầu khác."
            );
        }

        return Optional.of(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String idempotencyKey, String commandType, String actorScope, Object requestPayload, Object responsePayload
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;

        try {
            String hash = computeHash(requestPayload);
            String responseBody = objectMapper.writeValueAsString(responsePayload);

            ConsultationIdempotencyRecord record = ConsultationIdempotencyRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .idempotencyKey(idempotencyKey.trim())
                    .actorScope(actorScope)
                    .commandType(commandType)
                    .requestHash(hash)
                    .responseBody(responseBody)
                    .status("SUCCESS")
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build();

            recordRepository.save(record);
        } catch (Exception e) {
            log.warn("Failed to record idempotency record for key: {}", idempotencyKey, e);
        }
    }

    public <T> T deserialize(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Lỗi giải mã idempotency response", e);
        }
    }

    private String computeHash(Object payload) {
        if (payload == null) return "EMPTY";
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            return String.valueOf(payload.hashCode());
        }
    }
}
