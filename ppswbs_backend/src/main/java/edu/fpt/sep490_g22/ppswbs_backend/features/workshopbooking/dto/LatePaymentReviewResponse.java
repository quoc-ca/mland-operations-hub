package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import java.time.Instant;
import java.util.List;

/** {@code LatePaymentReview} of the cart contract (003.1 FR-040/FR-040a); no buyer contact data. */
public record LatePaymentReviewResponse(
        String reviewId,
        String groupCode,
        String outcome,
        String amount,
        Instant arrivedAt,
        List<String> failedConditions,
        boolean confirmAllowed,
        Instant earliestSessionAt,
        Instant resultDueAt,
        Instant decidedAt,
        String refundRequestReference
) {
}
