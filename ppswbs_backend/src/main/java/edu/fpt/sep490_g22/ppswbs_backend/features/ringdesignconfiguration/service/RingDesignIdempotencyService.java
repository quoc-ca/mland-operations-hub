package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignIdempotencyRecord;

import java.util.Optional;

public interface RingDesignIdempotencyService {

    Optional<RingDesignIdempotencyRecord> checkReplay(
            String idempotencyKey,
            String commandType,
            String actorScope,
            Object requestPayload
    );

    void record(
            String idempotencyKey,
            String commandType,
            String actorScope,
            Object requestPayload,
            int statusCode,
            Object responseBody
    );

    <T> T deserialize(String json, Class<T> clazz);
}
