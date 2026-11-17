package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

/** Point ledger entry kinds with their fixed deltas (data-model section 5). */
public enum LedgerEntryKind {
    HOLD,
    CONSUME,
    RELEASE,
    RESTORE,
    EARN
}
