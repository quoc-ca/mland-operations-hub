package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

public record SettlementIdempotencyResult(boolean replayed, String outcomeReference) {
    public SettlementIdempotencyResult {
        if (outcomeReference == null || outcomeReference.isBlank()) {
            throw new IllegalArgumentException("outcomeReference must not be blank");
        }
    }
}
