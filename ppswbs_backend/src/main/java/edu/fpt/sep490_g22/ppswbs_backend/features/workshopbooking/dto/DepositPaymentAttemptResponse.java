package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import java.time.Instant;

public record DepositPaymentAttemptResponse(
        String attemptReference,
        String merchantReference,
        long amountVnd,
        String currency,
        String paymentUrl,
        Instant expiresAt,
        String status) { }
