package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/** PII-free facts of one late-payment review, for 003.3 to recheck a refund amount (FR-040). */
public record LatePaymentReviewSnapshot(
        String reviewReference,
        String groupCode,
        String verifiedEvidenceReference,
        long amountVnd,
        String outcome
) {
}
