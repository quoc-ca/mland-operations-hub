package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

/** How a booking group's booking-time collection is settled (003.1 FR-036). */
public enum GroupPaymentMode {
    /** One VNPay payment for the whole group; the group collection is at least 5,000 VND. */
    VNPAY,
    /** Every ticket is free; confirmed internally without the gateway. */
    INTERNAL_FREE,
    /** The group collection is under the VNPay minimum; deferred to checkout and confirmed internally. */
    INTERNAL_DEFERRED
}
