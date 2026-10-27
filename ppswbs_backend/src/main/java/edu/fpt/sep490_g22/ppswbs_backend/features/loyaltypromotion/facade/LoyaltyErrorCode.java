package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/** Typed failure codes of the loyalty boundary (interfaces section 3). Transport mapping lives in the controller. */
public enum LoyaltyErrorCode {
    INVALID_INPUT,
    AUTHENTICATION_REQUIRED,
    ACCESS_DENIED,
    PROMOTION_NOT_FOUND,
    CODE_EXISTS,
    STALE_VERSION,
    IDEMPOTENCY_CONFLICT,
    QUOTA_LIMIT_CONFLICT,
    BENEFIT_STATE_CONFLICT,
    QUOTE_CHANGED,
    POINTS_LIMIT_CHANGED,
    PROMOTION_INELIGIBLE,
    POINTS_USE_NOT_ALLOWED,
    WORKSHOP_NET_AMOUNT_INVALID,
    DEPENDENCY_UNAVAILABLE,
    RETRYABLE_CONTENTION
}
