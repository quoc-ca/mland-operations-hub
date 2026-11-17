package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record SettlementNotificationRecipientQuery(String bookingReference) {
    public SettlementNotificationRecipientQuery {
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("bookingReference is required");
        }
        bookingReference = bookingReference.trim();
    }
}
