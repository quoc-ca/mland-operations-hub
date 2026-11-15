package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public enum RejectionReason {
    INSUFFICIENT_CAPACITY,
    SALE_NOT_ELIGIBLE,
    HOLD_MISSING_OR_EXPIRED,
    NOT_READY,
    INVALID_REPLACEMENT,
    INVALID_STATE,
    CANCELLATION_HANDOFF_UNRESOLVED,
    PACKAGE_NOT_PUBLISHED,
    PACKAGE_SELECTION_INVALID,
    TICKET_NOT_FOUND,
    TICKET_ALREADY_CHECKED_IN,
    /** 003.2 FR-035: a target session has no reschedule quota left for a customer change. */
    RESCHEDULE_QUOTA_EXHAUSTED,
    /** 003.2 FR-034: the replacement of a missed session starts after the old session's end plus 7 days. */
    LATE_ARRIVAL_WINDOW_EXPIRED,
    /** 003.2 FR-036: an OTA-origin ticket keeps the OTA's policy; cancel, replace and QR reissue refuse it. */
    OTA_TICKET_NOT_SUPPORTED
}
