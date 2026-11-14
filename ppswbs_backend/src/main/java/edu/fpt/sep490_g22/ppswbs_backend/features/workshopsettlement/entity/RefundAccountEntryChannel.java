package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

/** How the recipient account reached the system (003.3 FR-040). */
public enum RefundAccountEntryChannel {
    /** The single-use link sent to the payer. */
    LINK,
    /** Staff entered it at the counter, with actor and time recorded. */
    COUNTER
}
