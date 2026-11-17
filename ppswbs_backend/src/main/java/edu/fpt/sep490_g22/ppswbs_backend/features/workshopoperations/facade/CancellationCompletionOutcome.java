package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public record CancellationCompletionOutcome(
    OutcomeStatus status,
    RejectionReason reason
) {
    public static CancellationCompletionOutcome accepted() {
        return new CancellationCompletionOutcome(OutcomeStatus.ACCEPTED, null);
    }

    public static CancellationCompletionOutcome rejected(RejectionReason reason) {
        return new CancellationCompletionOutcome(OutcomeStatus.REJECTED, reason);
    }

    public static CancellationCompletionOutcome inProgress() {
        return new CancellationCompletionOutcome(OutcomeStatus.IN_PROGRESS, null);
    }
}
