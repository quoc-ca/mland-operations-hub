package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

/**
 * Service states for a WorkshopBooking.
 * <p>
 * - PENDING_PAYMENT: Booking created, 15-minute capacity hold active, awaiting deposit.
 * - CONFIRMED: Deposit received and verified; QR ticket issued.
 * - EXPIRED: Hold deadline passed without successful payment.
 * - CANCELLED: Guest/Member explicitly released an active PENDING_PAYMENT hold.
 * <p>
 * Reserved future states (not transitioned in this feature):
 * CHECKED_IN, COMPLETED, NO_SHOW.
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    EXPIRED,
    CANCELLED
}
