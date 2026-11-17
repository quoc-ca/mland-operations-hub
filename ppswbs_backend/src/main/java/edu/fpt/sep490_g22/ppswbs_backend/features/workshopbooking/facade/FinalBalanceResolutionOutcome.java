package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record FinalBalanceResolutionOutcome(
    BookingOutcomeStatus status,
    BookingRejectionReason reason
) {
    public static FinalBalanceResolutionOutcome accepted() {
        return new FinalBalanceResolutionOutcome(BookingOutcomeStatus.ACCEPTED, null);
    }

    public static FinalBalanceResolutionOutcome rejected(BookingRejectionReason reason) {
        return new FinalBalanceResolutionOutcome(BookingOutcomeStatus.REJECTED, reason);
    }
}
