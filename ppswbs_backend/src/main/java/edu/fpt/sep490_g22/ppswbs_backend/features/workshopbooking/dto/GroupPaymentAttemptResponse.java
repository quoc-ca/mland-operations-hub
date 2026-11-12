package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import java.time.Instant;

/**
 * The single gateway payment of a booking group (003.1 FR-038). {@code expiresAt} is the group's payment
 * deadline, two minutes before the hold ends; screens show this one and never mention the buffer (FR-032).
 */
public record GroupPaymentAttemptResponse(
        String attemptReference,
        String merchantReference,
        long amountVnd,
        String currency,
        String paymentUrl,
        Instant expiresAt,
        String status) {
}
