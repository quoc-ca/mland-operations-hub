package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade;

/**
 * Failure classifications for operational notification delivery handoffs from Feature 007.
 */
public enum DeliveryFailureCode {
    /** Network unreachable, connection refused, or SMTP timeout. */
    PROVIDER_UNAVAILABLE,
    /** Permanent SMTP rejection from the mail server (e.g. 5xx response, relay denied, quota). */
    PROVIDER_REJECTED,
    /** Malformed recipient email address or mailbox rejected/bounced. */
    RECIPIENT_INVALID,
    /** Unable to retrieve delivery snapshot payload from the owning Booking facade. */
    PAYLOAD_MISSING
}
