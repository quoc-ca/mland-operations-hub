package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade;

public record BookingNotificationDeliveryPayloadQuery(String notificationReference) {
    public BookingNotificationDeliveryPayloadQuery {
        if (notificationReference == null || notificationReference.isBlank()) {
            throw new IllegalArgumentException("notificationReference must not be blank");
        }
    }
}
