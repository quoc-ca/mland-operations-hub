package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/**
 * Verified (signed, 00/00) VNPay evidence for a booking-group payment whose amount or currency does not match the
 * attempt (003.1 FR-010f, decided 2026-11-16 F14). {@code evidenceReference} is the shared ingress receipt's delivery
 * reference. Carries no contact data and no raw callback.
 */
public record RecordMismatchedGroupPaymentEvidenceCommand(
        String bookingReference,
        String merchantReference,
        String evidenceReference,
        long receivedAmountVnd,
        String receivedCurrencyCode,
        String reason,
        String correlationReference
) { }
