package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/**
 * What happened to the group's voucher. The ticket's points are always handled on their own, whatever this says.
 * {@code EXPIRED}: the voucher was consumed and its revision window has ended, so it is not given back and never
 * extended. {@code ALREADY_RESTORED}: an earlier call for the same group already gave it back (or released the hold).
 */
public enum VoucherRestorationOutcome {
    RESTORED,
    EXPIRED,
    ALREADY_RESTORED
}
