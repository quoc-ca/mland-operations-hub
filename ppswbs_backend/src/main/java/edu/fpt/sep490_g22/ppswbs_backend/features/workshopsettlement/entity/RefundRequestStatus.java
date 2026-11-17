package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

/** Lifecycle of a bank refund request (003.3 FR-039). */
public enum RefundRequestStatus {
    /** Waiting for the customer's account, by link or counter entry; reminders on days 3 and 7. */
    PENDING_ACCOUNT,
    PENDING_APPROVAL,
    PENDING_TRANSFER,
    PENDING_RECONCILIATION,
    REFUNDED,
    /** Closed after 30 days without a reply; a Manager reopens it when the customer returns. */
    CLOSED_UNREACHABLE
}
