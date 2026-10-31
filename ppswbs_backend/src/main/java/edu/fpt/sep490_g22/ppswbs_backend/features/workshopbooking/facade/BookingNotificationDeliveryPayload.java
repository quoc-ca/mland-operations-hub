package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.util.List;

/**
 * Immutable render-ready notification delivery snapshot queried by Feature 007.
 * Callable only after a matching handoff.
 * Stored recipient, rendered ticket artifacts, and booking-access link must not be included
 * in the handoff event or provider/audit logs.
 */
public record BookingNotificationDeliveryPayload(
    String notificationKind,
    String storedRecipient,
    String renderedBookingContent,
    List<String> renderedTicketArtifacts,
    String bookingAccessLink,
    String correlationReference
) {}
