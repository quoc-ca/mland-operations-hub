package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/** Authoritative lifecycle outcome verified by the source owner (interfaces section 4). */
public enum OutcomeKind {
    RETAIL_FULLY_PAID,
    RETAIL_ZERO_ACCEPTED,
    WORKSHOP_DEPOSIT_ACCEPTED,
    WORKSHOP_COMPLETED_AND_FULLY_PAID,
    UNPAID_EXPIRY_CONFIRMED,
    VALID_WORKSHOP_CANCELLATION,
    WORKSHOP_NO_SHOW_FINAL
}
