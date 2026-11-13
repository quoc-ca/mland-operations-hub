package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

/** Why Mland owes this money back (003.3 FR-038, FR-038a). */
public enum RefundReason {
    MLAND_CANCELLATION,
    LATE_PAYMENT_NOT_RECONFIRMED,
    OVERCHARGE_AFTER_FINALIZED,
    EXCESS_OR_DUPLICATE_MONEY,
    OTA_ADDON_OVERCHARGE
}
