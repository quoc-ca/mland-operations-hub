package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.time.Instant;

/**
 * Lifecycle outcome already verified and serialised by the source owner. Loyalty never interprets a
 * gateway callback or a browser redirect; {@code proofRef} is a minimal reference, not a raw payload.
 * {@code partRef} (Session 2026-11-12, D-011) names the settlement part of a part outcome
 * ({@link OutcomeKind#isPartOutcome()}); it is required for those and must be null for every other outcome.
 */
public record AuthoritativeOutcome(SourceKind sourceKind, String sourceRef, String ownerModule,
                                   String sourceOutcomeKey, long sourceOutcomeVersion, OutcomeKind outcome,
                                   Instant decisionAt, Instant originalDeadline, String snapshotRef,
                                   boolean memberOrigin, String proofRef, String partRef) {

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
        boolean retailOutcome = outcome == OutcomeKind.RETAIL_FULLY_PAID || outcome == OutcomeKind.RETAIL_ZERO_ACCEPTED
                || outcome == OutcomeKind.RETAIL_CANCELLED_BEFORE_PAYMENT
                || (outcome.isPartOutcome() && !outcome.isWorkshopTicketOutcome());
        boolean workshopOutcome = outcome == OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED
                || outcome == OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID
                || outcome == OutcomeKind.VALID_WORKSHOP_CANCELLATION
                || outcome == OutcomeKind.WORKSHOP_NO_SHOW_FINAL
                || outcome.isWorkshopTicketOutcome();
        if ((retailOutcome && sourceKind != SourceKind.RETAIL) || (workshopOutcome && sourceKind != SourceKind.WORKSHOP)) {
            throw LoyaltyException.invalidField("outcome", "Outcome does not match the source kind.");
        }
        partRef = partRef == null ? null : partRef.trim();
        if (outcome.isPartOutcome() != (partRef != null && !partRef.isEmpty())) {
            throw LoyaltyException.invalidField("partRef", outcome.isPartOutcome()
                    ? "A part outcome needs the part reference." : "Only a part outcome carries a part reference.");
        }
    }

    /** Shape before settlement parts, kept so callers keep compiling: an outcome of the whole transaction. */
    public AuthoritativeOutcome(SourceKind sourceKind, String sourceRef, String ownerModule,
                                String sourceOutcomeKey, long sourceOutcomeVersion, OutcomeKind outcome,
                                Instant decisionAt, Instant originalDeadline, String snapshotRef,
                                boolean memberOrigin, String proofRef) {
        this(sourceKind, sourceRef, ownerModule, sourceOutcomeKey, sourceOutcomeVersion, outcome, decisionAt,
                originalDeadline, snapshotRef, memberOrigin, proofRef, null);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
