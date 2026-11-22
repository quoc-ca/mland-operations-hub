package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

/** A voucher needs a code and has quotas; an automatic discount has neither (products, or workshop packages only, FR-044). */
public enum ProgramKind {
    VOUCHER,
    AUTO_DISCOUNT
}
