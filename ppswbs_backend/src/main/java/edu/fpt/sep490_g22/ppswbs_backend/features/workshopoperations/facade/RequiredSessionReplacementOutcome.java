package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public record RequiredSessionReplacementOutcome(
    OutcomeStatus status,
    RejectionReason reason
) {
    public static RequiredSessionReplacementOutcome accepted() {
        return new RequiredSessionReplacementOutcome(OutcomeStatus.ACCEPTED, null);
    }

    public static RequiredSessionReplacementOutcome rejected(RejectionReason reason) {
        return new RequiredSessionReplacementOutcome(OutcomeStatus.REJECTED, reason);
    }

    public static RequiredSessionReplacementOutcome inProgress() {
        return new RequiredSessionReplacementOutcome(OutcomeStatus.IN_PROGRESS, null);
    }
}
