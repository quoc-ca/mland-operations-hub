package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Trusted input of the accept transaction. {@code memberOrigin} is {@code true} only for a booking/order made
 * directly on the website; guest-import identities never qualify and are not retrofitted. Since D-014
 * (Session 2026-11-14) that includes a Guest's direct workshop booking: its trusted context carries
 * {@link TrustedSourceContext#GUEST}, and the snapshot is kept without a Member (automatic discount only). {@code quoteRef} and {@code termsDigest} are source-owned acknowledgement proofs.
 * {@code originalDeadline} is the owner's hold deadline; loyalty sets no upper bound on it (FE-08 uses 15 minutes for
 * VNPay and 72 hours for cash at the counter).
 * {@code parts} (Session 2026-11-12, D-011, retail only, optional) split the checkout into branch orders that are
 * settled, earned and released one by one; every line belongs to exactly one part. Empty = the whole checkout is
 * one unit, as before.
 */
public record AcceptedSourceContext(TrustedSourceContext trusted, String sourceRef, long sourceRevision,
                                    boolean memberOrigin, Instant acceptedAt, Instant originalDeadline,
                                    String quoteRef, String termsDigest, List<SettlementPart> parts) {

    public static final int MAX_PARTS = 50;

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
        if (parts != null && parts.stream().anyMatch(java.util.Objects::isNull)) {
            throw LoyaltyException.invalidField("parts", "Parts must not be empty entries.");
        }
        parts = parts == null ? List.of() : List.copyOf(parts);
        if (!parts.isEmpty()) {
            requireCoveringParts(trusted, parts);
        }
    }

    /** Shape before settlement parts, kept so callers keep compiling: the checkout is one unit. */
    public AcceptedSourceContext(TrustedSourceContext trusted, String sourceRef, long sourceRevision,
                                 boolean memberOrigin, Instant acceptedAt, Instant originalDeadline,
                                 String quoteRef, String termsDigest) {
        this(trusted, sourceRef, sourceRevision, memberOrigin, acceptedAt, originalDeadline, quoteRef, termsDigest,
                List.of());
    }

    private static void requireCoveringParts(TrustedSourceContext trusted, List<SettlementPart> parts) {
        if (trusted.sourceKind() != SourceKind.RETAIL) {
            throw LoyaltyException.invalidField("parts", "Only a retail checkout can be split into parts.");
        }
        if (parts.size() > MAX_PARTS) {
            throw LoyaltyException.invalidField("parts", "Too many parts.");
        }
        Set<String> refs = new HashSet<>();
        Set<Integer> covered = new HashSet<>();
        int lines = trusted.lines().size();
        for (SettlementPart part : parts) {
            if (!refs.add(part.partRef())) {
                throw LoyaltyException.invalidField("parts", "Part references must be distinct.");
            }
            for (int index : part.lineIndexes()) {
                if (index >= lines || !covered.add(index)) {
                    throw LoyaltyException.invalidField("parts", "Every line must belong to exactly one part.");
                }
            }
        }
        if (covered.size() != lines) {
            throw LoyaltyException.invalidField("parts", "Every line must belong to exactly one part.");
        }
    }
}
