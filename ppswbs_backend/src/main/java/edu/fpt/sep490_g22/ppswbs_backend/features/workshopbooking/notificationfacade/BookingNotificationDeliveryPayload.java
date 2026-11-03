package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade;

import java.util.List;

/**
 * Render-ready payload available to Feature 007 only after its matching handoff.
 * Recipient, ticket artifacts, and booking-access URL are excluded from the event,
 * business audit, and application/provider logs. The URL is returned only while
 * its Magic Link remains active and is scrubbed from persistence after expiry/revocation.
 */
public record BookingNotificationDeliveryPayload(
        String notificationKind,
        String storedRecipient,
        String renderedBookingContent,
        List<String> renderedTicketArtifacts,
        String bookingAccessLink,
        String correlationReference
) { }
