package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record ConfirmedBookingFinancialProjectionQuery(
    String bookingReference
) {
    public ConfirmedBookingFinancialProjectionQuery {
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("bookingReference must not be blank");
        }
    }
}
