package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public enum BookingRejectionReason {
    INSUFFICIENT_CAPACITY,
    SALE_NOT_ELIGIBLE,
    HOLD_MISSING_OR_EXPIRED,
    NOT_READY,
    INVALID_REPLACEMENT,
    INVALID_STATE,
    CANCELLATION_HANDOFF_UNRESOLVED,
    BOOKING_NOT_FOUND,
    ALREADY_FINALIZED,
    PAYMENT_MISMATCH
}
