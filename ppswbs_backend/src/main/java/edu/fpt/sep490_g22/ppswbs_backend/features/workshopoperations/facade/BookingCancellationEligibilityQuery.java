package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public record BookingCancellationEligibilityQuery(String bookingReference) {
    public BookingCancellationEligibilityQuery {
        if (bookingReference == null || bookingReference.isBlank()) throw new IllegalArgumentException("bookingReference is required");
        bookingReference = bookingReference.trim();
    }
}
