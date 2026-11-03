package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import java.time.Instant;

public record DepositPaymentRecoveryCandidate(
        Long attemptId, String attemptReference, String merchantReference, Long amountVnd,
        String providerCreateDate, Instant createdAt, Instant expiresAt,
        String bookingCode, BookingStatus bookingStatus, Instant holdExpiresAt) {
}
