package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

/** Lifecycle of one FE-08 inventory hold. Only HELD contributes to availability. */
public enum RetailVariantHoldStatus {
    HELD,
    SETTLED,
    RELEASED,
    EXPIRED
}
