package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import lombok.Builder;

@Builder
public record BookingEligibilitySnapshot(
    Long bookingId,
    String bookingCode,
    Long memberAccountId,
    Long packageId,
    String packageName,
    String bookingStatus,
    String paymentStatus,
    boolean eligibleForDesign
) {}
