package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public record CapacityReleaseOutcome(
    OutcomeStatus status,
    RejectionReason reason
) {
    public static CapacityReleaseOutcome accepted() {
        return new CapacityReleaseOutcome(OutcomeStatus.ACCEPTED, null);
    }

    public static CapacityReleaseOutcome rejected(RejectionReason reason) {
        return new CapacityReleaseOutcome(OutcomeStatus.REJECTED, reason);
    }

    public static CapacityReleaseOutcome inProgress() {
        return new CapacityReleaseOutcome(OutcomeStatus.IN_PROGRESS, null);
    }
}
