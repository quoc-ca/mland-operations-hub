package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import java.time.Instant;
import java.util.List;

public record WorkshopBookingQuoteResponse(
        String quoteRef,
        Instant expiresAt,
        String grossAmount,
        String programDiscount,
        String pointsUsed,
        String pointDiscount,
        String postPromotionAmount,
        String depositAmount,
        String remainingAmount,
        String earnBase,
        String termsDigest,
        List<String> reasons) {
}
