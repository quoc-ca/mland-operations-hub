package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/**
 * T007: Approved post-commit notification handoff event published from Workshop Booking to Feature 007.
 * <p>
 * Invariants:
 * - Published ONLY after the owning local transaction commits.
 * - Strictly reference-only: contains no recipient address, ticket credential, QR payload, Magic-Link value,
 *   payment data, or unnecessary PII.
 * - Feature 007 deduplicates by notificationReference and retrieves the immutable render-ready payload via
 *   WorkshopBookingFacade.getBookingNotificationDeliveryPayload.
 */
public record BookingNotificationReady(
    String notificationReference,
    String bookingReference,
    String notificationKind,
    String correlationReference
) {}
