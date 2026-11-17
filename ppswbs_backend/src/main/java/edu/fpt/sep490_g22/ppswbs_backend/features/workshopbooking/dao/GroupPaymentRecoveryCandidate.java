package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;

import java.time.Instant;

/** A group payment attempt that may still be recovered by QueryDR while its group is held (003.1 FR-032). */
public record GroupPaymentRecoveryCandidate(
        Long attemptId, String attemptReference, String merchantReference, long amountVnd,
        String providerCreateDate, Instant expiresAt,
        String groupCode, BookingStatus groupStatus, Instant holdExpiresAt, Instant paymentDeadlineAt) {
}
