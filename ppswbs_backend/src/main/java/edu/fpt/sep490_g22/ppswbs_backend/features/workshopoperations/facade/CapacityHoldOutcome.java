package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CapacityHoldOutcome(
    OutcomeStatus status,
    List<UUID> holdReferences,
    Instant expiresAt,
    RejectionReason reason
) {
    public CapacityHoldOutcome {
        holdReferences = holdReferences != null ? List.copyOf(holdReferences) : List.of();
    }

    public static CapacityHoldOutcome accepted(List<UUID> holdReferences, Instant expiresAt) {
        return new CapacityHoldOutcome(OutcomeStatus.ACCEPTED, holdReferences, expiresAt, null);
    }

    public static CapacityHoldOutcome rejected(RejectionReason reason) {
        return new CapacityHoldOutcome(OutcomeStatus.REJECTED, List.of(), null, reason);
    }

    public static CapacityHoldOutcome inProgress() {
        return new CapacityHoldOutcome(OutcomeStatus.IN_PROGRESS, List.of(), null, null);
    }
}
