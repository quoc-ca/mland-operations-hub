package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.time.Instant;

/**
 * Lifecycle outcome already verified and serialised by the source owner. Loyalty never interprets a
 * gateway callback or a browser redirect; {@code proofRef} is a minimal reference, not a raw payload.
 */
public record AuthoritativeOutcome(SourceKind sourceKind, String sourceRef, String ownerModule,
                                   String sourceOutcomeKey, long sourceOutcomeVersion, OutcomeKind outcome,
                                   Instant decisionAt, Instant originalDeadline, String snapshotRef,
                                   boolean memberOrigin, String proofRef) {

    public AuthoritativeOutcome {
        if (sourceKind == null || outcome == null || decisionAt == null) {
            throw LoyaltyException.invalidField("outcome", "Outcome is incomplete.");
        }
        if (blank(sourceRef) || blank(ownerModule) || blank(sourceOutcomeKey) || blank(snapshotRef) || blank(proofRef)) {
            throw LoyaltyException.invalidField("outcome", "Outcome references are required.");
        }
        if (sourceOutcomeVersion < 0) {
            throw LoyaltyException.invalidField("sourceOutcomeVersion", "Outcome version must not be negative.");
        }
        boolean retailOutcome = outcome == OutcomeKind.RETAIL_FULLY_PAID || outcome == OutcomeKind.RETAIL_ZERO_ACCEPTED;
        boolean workshopOutcome = outcome == OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED
                || outcome == OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID
                || outcome == OutcomeKind.VALID_WORKSHOP_CANCELLATION
                || outcome == OutcomeKind.WORKSHOP_NO_SHOW_FINAL;
        if ((retailOutcome && sourceKind != SourceKind.RETAIL) || (workshopOutcome && sourceKind != SourceKind.WORKSHOP)) {
            throw LoyaltyException.invalidField("outcome", "Outcome does not match the source kind.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
