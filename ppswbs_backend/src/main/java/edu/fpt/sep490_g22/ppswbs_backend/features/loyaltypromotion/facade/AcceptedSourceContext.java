package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.time.Instant;

/**
 * Trusted input of the accept transaction. {@code memberOrigin} is {@code true} only for a Member
 * booking/order made directly on the website; guest-import identities never qualify and are not
 * retrofitted. {@code quoteRef} and {@code termsDigest} are source-owned acknowledgement proofs.
 */
public record AcceptedSourceContext(TrustedSourceContext trusted, String sourceRef, long sourceRevision,
                                    boolean memberOrigin, Instant acceptedAt, Instant originalDeadline,
                                    String quoteRef, String termsDigest) {

    public AcceptedSourceContext {
        if (trusted == null || acceptedAt == null || originalDeadline == null) {
            throw LoyaltyException.invalidField("context", "Accepted context is incomplete.");
        }
        if (sourceRef == null || sourceRef.isBlank() || sourceRef.length() > 128) {
            throw LoyaltyException.invalidField("sourceRef", "Source reference is required (max 128 characters).");
        }
        if (sourceRevision < 0) {
            throw LoyaltyException.invalidField("sourceRevision", "Source revision must not be negative.");
        }
        if (quoteRef == null || quoteRef.isBlank() || termsDigest == null || termsDigest.isBlank()) {
            throw LoyaltyException.invalidField("quoteRef", "Accepted quote reference and terms digest are required.");
        }
        if (!originalDeadline.isAfter(acceptedAt)) {
            throw LoyaltyException.invalidField("originalDeadline", "Original deadline must be after acceptance.");
        }
    }
}
