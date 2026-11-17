package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/** State of one benefit component (points or voucher), data-model section 4. */
public enum ComponentState {
    NONE,
    HELD,
    CONSUMED,
    RELEASED,
    RESTORED
}
