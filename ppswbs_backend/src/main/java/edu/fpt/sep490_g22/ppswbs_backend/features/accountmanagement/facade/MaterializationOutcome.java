package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.time.Instant;

public record MaterializationOutcome(
    Status status,
    String transientUrl,
    Instant expiresAt,
    String safeReason
) {
    public enum Status {
        AVAILABLE,
        STALE,
        UNAVAILABLE
    }

    public static MaterializationOutcome available(String transientUrl, Instant expiresAt) {
        return new MaterializationOutcome(Status.AVAILABLE, transientUrl, expiresAt, null);
    }

    public static MaterializationOutcome stale(String safeReason) {
        return new MaterializationOutcome(Status.STALE, null, null, safeReason);
    }

    public static MaterializationOutcome unavailable(String safeReason) {
        return new MaterializationOutcome(Status.UNAVAILABLE, null, null, safeReason);
    }
}
