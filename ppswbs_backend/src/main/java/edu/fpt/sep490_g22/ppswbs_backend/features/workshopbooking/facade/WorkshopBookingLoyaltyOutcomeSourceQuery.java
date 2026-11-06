package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record WorkshopBookingLoyaltyOutcomeSourceQuery(String bookingReference) {
    public WorkshopBookingLoyaltyOutcomeSourceQuery {
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("bookingReference is required");
        }
        bookingReference = bookingReference.trim();
    }
}
