package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/** Dimension of a programme target; dimensions combine with AND, values inside one dimension with OR. */
public enum TargetDimension {
    PRODUCT,
    VARIANT,
    PACKAGE,
    BRANCH,
    WORKSHOP_DATE,
    SESSION
}
