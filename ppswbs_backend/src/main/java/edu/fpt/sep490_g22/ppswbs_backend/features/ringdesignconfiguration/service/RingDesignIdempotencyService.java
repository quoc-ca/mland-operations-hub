package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingDesignIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignIdempotencyRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RingDesignIdempotencyService {

    private final RingDesignIdempotencyRecordRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<RingDesignIdempotencyRecord> checkReplay(
            String idempotencyKey,
            String commandType,
            String actorScope,
            Object requestPayload
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        Optional<RingDesignIdempotencyRecord> existingOpt = idempotencyRepository
                .findByIdempotencyKeyAndCommandTypeAndActorScope(idempotencyKey.trim(), commandType, actorScope);

        if (existingOpt.isPresent()) {
            RingDesignIdempotencyRecord record = existingOpt.get();
            String currentHash = hashPayload(requestPayload);
            if (!record.getRequestHash().equals(currentHash)) {
                throw RingDesignException.conflict("IDEMPOTENCY_CONFLICT",
                        "Idempotency key trùng lặp nhưng payload của lệnh đã bị thay đổi.");
            }
            return Optional.of(record);
        }

        return Optional.empty();
    }

    @Transactional
    public void record(
            String idempotencyKey,
            String commandType,
            String actorScope,
            Object requestPayload,
            int statusCode,
            Object responseBody
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        try {
            String jsonResponse = objectMapper.writeValueAsString(responseBody);
            String hash = hashPayload(requestPayload);

            RingDesignIdempotencyRecord record = RingDesignIdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey.trim())
                    .commandType(commandType)
                    .actorScope(actorScope)
                    .requestHash(hash)
                    .responseStatus(statusCode)
                    .responseBody(jsonResponse)
                    .build();

            idempotencyRepository.save(record);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize idempotency response body", e);
        }
    }

    public <T> T deserialize(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotency response body", e);
        }
    }

    private String hashPayload(Object payload) {
        try {
            String json = payload != null ? objectMapper.writeValueAsString(payload) : "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "empty-hash";
        }
    }
}

