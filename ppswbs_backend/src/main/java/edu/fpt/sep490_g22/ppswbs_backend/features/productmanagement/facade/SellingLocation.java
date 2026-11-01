package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

/** Immutable location facts used by product-management selling flows. */
public record SellingLocation(Long id, String displayName, String addressSnapshot) {}
