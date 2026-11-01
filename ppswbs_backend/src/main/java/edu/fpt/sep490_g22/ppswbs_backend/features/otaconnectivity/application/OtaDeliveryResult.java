package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import java.time.Instant;

/** Sanitized outcome metadata; provider-specific bodies and headers are excluded. */
public record OtaDeliveryResult(
        int attemptNumber,
        Instant startedAt,
        Instant completedAt,
        OtaDeliveryOutcome outcome,
        String correlationReference,
        String failureCategory) {
}
