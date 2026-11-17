package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record BookingCancellationOutcome(BookingOutcomeStatus status, BookingRejectionReason reason) {
    public static BookingCancellationOutcome accepted() {
        return new BookingCancellationOutcome(BookingOutcomeStatus.ACCEPTED, null);
    }

    public static BookingCancellationOutcome rejected(BookingRejectionReason reason) {
        return new BookingCancellationOutcome(BookingOutcomeStatus.REJECTED, reason);
    }
}
