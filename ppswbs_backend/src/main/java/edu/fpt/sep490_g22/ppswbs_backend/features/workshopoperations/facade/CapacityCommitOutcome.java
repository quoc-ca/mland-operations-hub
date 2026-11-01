package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public record CapacityCommitOutcome(
    OutcomeStatus status,
    RejectionReason reason
) {
    public static CapacityCommitOutcome accepted() {
        return new CapacityCommitOutcome(OutcomeStatus.ACCEPTED, null);
    }

    public static CapacityCommitOutcome rejected(RejectionReason reason) {
        return new CapacityCommitOutcome(OutcomeStatus.REJECTED, reason);
    }

    public static CapacityCommitOutcome inProgress() {
        return new CapacityCommitOutcome(OutcomeStatus.IN_PROGRESS, null);
    }
}
