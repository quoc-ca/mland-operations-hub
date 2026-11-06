package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade;

/** Narrow Booking boundary for the Feature 007 notification consumer only. */
public interface WorkshopBookingNotificationFacade {

    /** Read the protected snapshot only after receiving its matching handoff event. */
    BookingNotificationDeliveryPayload getBookingNotificationDeliveryPayload(
            BookingNotificationDeliveryPayloadQuery query);
}
