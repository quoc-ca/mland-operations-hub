package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record BookingRefundStatusResult(BookingOutcomeStatus status, BookingRejectionReason reason) {
    public static BookingRefundStatusResult accepted() {
        return new BookingRefundStatusResult(BookingOutcomeStatus.ACCEPTED, null);
    }

    public static BookingRefundStatusResult rejected(BookingRejectionReason reason) {
        return new BookingRefundStatusResult(BookingOutcomeStatus.REJECTED, reason);
    }
}
