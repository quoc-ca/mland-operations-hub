package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade;

import java.time.Instant;

/**
 * Terminal operational delivery failure event published by Feature 007 to Feature 003.1 when technical retries
 * are exhausted without successful provider acceptance.
 * <p>
 * Strictly reference-only (D13 & Modulith Invariants): contains no recipient address, rendered HTML/text,
 * Magic-Link token, ticket/QR artifact data, or payment secrets.
 */
public record BookingNotificationDeliveryFailed(
        String notificationReference,
        String bookingReference,
        String notificationKind,
        DeliveryFailureCode failureCode,
        Instant failedAt,
        String correlationReference
) { }
