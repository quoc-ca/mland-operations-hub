package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade;

/**
 * Reference-only post-commit handoff to Feature 007. It contains no recipient,
 * ticket credential, QR payload, Magic-Link value, payment data, or unnecessary PII.
 */
public record BookingNotificationReady(
        String notificationReference,
        String bookingReference,
        String notificationKind,
        String correlationReference
) { }
