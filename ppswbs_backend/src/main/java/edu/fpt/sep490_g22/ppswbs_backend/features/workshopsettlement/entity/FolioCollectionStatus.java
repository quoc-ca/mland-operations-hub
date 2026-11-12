package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

/** State of one folio collection (003.3 FR-031, FR-032). Only {@code CONFIRMED} reduces a balance. */
public enum FolioCollectionStatus {
    /** Recorded but not yet proven; the balance is unchanged and the product is not handed over. */
    PENDING_VERIFICATION,
    CONFIRMED,
    FAILED,
    VOIDED
}
