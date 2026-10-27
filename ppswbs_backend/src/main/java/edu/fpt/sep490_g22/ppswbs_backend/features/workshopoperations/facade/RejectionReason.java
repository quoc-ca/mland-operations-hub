package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public enum RejectionReason {
    INSUFFICIENT_CAPACITY,
    SALE_NOT_ELIGIBLE,
    HOLD_MISSING_OR_EXPIRED,
    NOT_READY,
    INVALID_REPLACEMENT,
    INVALID_STATE,
    CANCELLATION_HANDOFF_UNRESOLVED
}
