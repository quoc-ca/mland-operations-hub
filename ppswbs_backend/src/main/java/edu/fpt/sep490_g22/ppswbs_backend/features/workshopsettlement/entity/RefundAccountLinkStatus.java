package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

/** State of one account-collection link; only one is ACTIVE per request (003.3 FR-040). */
public enum RefundAccountLinkStatus {
    ACTIVE,
    USED,
    REVOKED,
    EXPIRED
}
