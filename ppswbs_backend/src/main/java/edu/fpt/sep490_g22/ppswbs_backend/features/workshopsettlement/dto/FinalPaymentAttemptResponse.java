package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import java.time.Instant;

public record FinalPaymentAttemptResponse(
        String attemptReference,
        String merchantReference,
        long amountVnd,
        String currency,
        String paymentUrl,
        Instant expiresAt,
        String status) { }
