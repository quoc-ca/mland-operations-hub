package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record BookingServiceOutcomeResult(BookingOutcomeStatus status, BookingRejectionReason reason) {
    public static BookingServiceOutcomeResult accepted() {
        return new BookingServiceOutcomeResult(BookingOutcomeStatus.ACCEPTED, null);
    }

    public static BookingServiceOutcomeResult rejected(BookingRejectionReason reason) {
        return new BookingServiceOutcomeResult(BookingOutcomeStatus.REJECTED, reason);
    }
}
