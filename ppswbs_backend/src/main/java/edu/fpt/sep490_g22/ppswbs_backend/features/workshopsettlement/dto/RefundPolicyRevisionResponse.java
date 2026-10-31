package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundPolicyRevision;

import java.time.Instant;

public record RefundPolicyRevisionResponse(String policyReference, Instant effectiveAt,
                                           Integer wholeBookingRefundPercent) {
    public static RefundPolicyRevisionResponse from(SettlementRefundPolicyRevision revision) {
        return new RefundPolicyRevisionResponse(revision.getPolicyReference(), revision.getEffectiveAt(),
                revision.getWholeBookingRefundPercent());
    }
}
