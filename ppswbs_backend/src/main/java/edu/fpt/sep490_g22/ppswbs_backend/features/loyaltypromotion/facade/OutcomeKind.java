package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/** Authoritative lifecycle outcome verified by the source owner (interfaces section 4). */
public enum OutcomeKind {
    RETAIL_FULLY_PAID,
    RETAIL_ZERO_ACCEPTED,
    /**
     * The Member cancelled a retail order before any payment, confirmed by FE-08 under its order lock (no payment
     * pending or captured). Releases held voucher/points and closes the earn, like an unpaid expiry (2026-10-30).
     */
    RETAIL_CANCELLED_BEFORE_PAYMENT,
    WORKSHOP_DEPOSIT_ACCEPTED,
    WORKSHOP_COMPLETED_AND_FULLY_PAID,
    UNPAID_EXPIRY_CONFIRMED,
    VALID_WORKSHOP_CANCELLATION,
    WORKSHOP_NO_SHOW_FINAL
}
