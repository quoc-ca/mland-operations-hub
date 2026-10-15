package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

/**
 * Financial payment states for a WorkshopBooking.
 * <p>
 * - UNPAID: No successful deposit received.
 * - DEPOSIT_PAID: Deposit confirmed after successful payment callback.
 * - FULLY_PAID: Reserved for future final-payment feature.
 * - REFUND_PENDING: Reserved for future refund feature.
 * - REFUNDED: Reserved for future refund feature.
 * <p>
 * Note: FAILED belongs only to PaymentAttempt, not to booking financial state.
 */
public enum BookingPaymentStatus {
    UNPAID,
    DEPOSIT_PAID,
    FULLY_PAID,
    REFUND_PENDING,
    REFUNDED
}
