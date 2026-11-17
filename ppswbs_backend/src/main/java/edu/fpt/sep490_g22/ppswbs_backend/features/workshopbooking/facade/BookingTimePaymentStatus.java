package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/** Verification state of one online booking-time VNPay payment (003.1 T150). */
public enum BookingTimePaymentStatus {
    /** Attempt SUCCESS and the group CONFIRMED. */
    CONFIRMED,
    /** Attempt SUCCESS but the group is not CONFIRMED (late payment or expired hold, awaiting FR-040 review). */
    LATE_PENDING_REVIEW,
    /** Attempt PENDING: the IPN has not arrived or has not been verified. */
    UNVERIFIED
}
