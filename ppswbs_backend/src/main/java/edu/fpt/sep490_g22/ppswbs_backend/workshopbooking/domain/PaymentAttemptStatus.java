package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

/**
 * Payment state for a single PaymentAttempt.
 * FAILED belongs here, not on the booking financial state.
 */
public enum PaymentAttemptStatus {
    INITIATED,
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED
}
