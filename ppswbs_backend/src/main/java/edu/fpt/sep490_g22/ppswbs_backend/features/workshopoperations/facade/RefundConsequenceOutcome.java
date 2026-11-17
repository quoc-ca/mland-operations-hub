package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public record RefundConsequenceOutcome(
    OutcomeStatus status,
    RejectionReason reason
) {
    public static RefundConsequenceOutcome accepted() {
        return new RefundConsequenceOutcome(OutcomeStatus.ACCEPTED, null);
    }

    public static RefundConsequenceOutcome rejected(RejectionReason reason) {
        return new RefundConsequenceOutcome(OutcomeStatus.REJECTED, reason);
    }

    public static RefundConsequenceOutcome inProgress() {
        return new RefundConsequenceOutcome(OutcomeStatus.IN_PROGRESS, null);
    }
}
