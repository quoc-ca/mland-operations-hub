package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * Facts the source owner verified on the server for a preview. Retail carries {@code lines} and a
 * separate {@code shippingAmount}; workshop carries {@code workshop}. Currency is always VND.
 *
 * <p>{@code memberId} is {@link #GUEST} (0) for a workshop booking made by a Guest who is not signed in
 * (Session 2026-11-14, FR-044): a workshop automatic discount applies to every direct customer, so the preview
 * must be answerable without a Member. A Guest context never carries points or a voucher — both stay
 * Member-only — and it is refused for a retail checkout, which has no such rule.
 */
public record TrustedSourceContext(SourceKind sourceKind, long memberId, Instant at,
                                   List<SourceLine> lines, WorkshopFact workshop, BigInteger shippingAmount) {

    public static final String CURRENCY = "VND";

    /** No Member behind this context. */
    public static final long GUEST = 0L;

    public TrustedSourceContext {
        if (sourceKind == null || memberId < GUEST || at == null) {
            throw LoyaltyException.invalidField("context", "Source kind, member and time are required.");
        }
        if (memberId == GUEST && sourceKind != SourceKind.WORKSHOP) {
            throw LoyaltyException.invalidField("memberId", "A member is required for this kind of transaction.");
        }
        lines = lines == null ? List.of() : List.copyOf(lines);
        shippingAmount = shippingAmount == null ? BigInteger.ZERO : shippingAmount;
        if (shippingAmount.signum() < 0) {
            throw LoyaltyException.invalidField("shippingAmount", "Shipping must not be negative.");
        }
        if (sourceKind == SourceKind.RETAIL && (lines.isEmpty() || workshop != null)) {
            throw LoyaltyException.invalidField("context", "A retail context needs lines and no workshop fact.");
        }
        if (sourceKind == SourceKind.WORKSHOP
                && (workshop == null || !lines.isEmpty() || shippingAmount.signum() != 0)) {
            throw LoyaltyException.invalidField("context", "A workshop context needs a workshop fact only.");
        }
    }

    /** No Member is signed in: only an automatic discount can apply, never a voucher or points. */
    public boolean isGuest() {
        return memberId == GUEST;
    }
}
