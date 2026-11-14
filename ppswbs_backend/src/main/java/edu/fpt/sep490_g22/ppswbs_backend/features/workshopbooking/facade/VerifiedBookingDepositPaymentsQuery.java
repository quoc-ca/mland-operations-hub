package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record VerifiedBookingDepositPaymentsQuery(String bookingReference) {
    public VerifiedBookingDepositPaymentsQuery {
        if (bookingReference == null || bookingReference.isBlank()) throw new IllegalArgumentException("bookingReference is required");
    }
}
