package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.time.Instant;

/**
 * A late-payment review entered the Manager queue (003.1 FR-040a). References only: no buyer contact, payment
 * payload, QR or link. Published after commit.
 */
public record LatePaymentReviewQueued(
        String reviewReference,
        String groupReference,
        Instant earliestSessionAt,
        Instant resultDueAt,
        String correlationReference
) {
}
