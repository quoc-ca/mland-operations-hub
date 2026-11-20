package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record PaymentFailureOutcome(
        BookingOutcomeStatus status,
        BookingRejectionReason reason
) {
    public static PaymentFailureOutcome accepted() {
        return new PaymentFailureOutcome(BookingOutcomeStatus.ACCEPTED, null);
    }

    public static PaymentFailureOutcome rejected(BookingRejectionReason reason) {
        return new PaymentFailureOutcome(BookingOutcomeStatus.REJECTED, reason);
    }
}
