package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.util.List;

/**
 * Confirmed booking financial projection required by 003.3.
 * Monetary amounts are whole VND.
 * Contains no customer contact PII, raw provider evidence, or ticket payload.
 */
public record ConfirmedBookingFinancialProjection(
    String packageReference,
    Long frozenPackagePrice,
    Long frozenPostPromotionAmount,
    Long acceptedDepositAmount,
    Long frozenRemainingAmount,
    Integer participantCount,
    List<String> requiredSessionReferences,
    Long bookingLocationReference
) {}
