package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * Facts the source owner verified on the server for a preview. Retail carries {@code lines} and a
 * separate {@code shippingAmount}; workshop carries {@code workshop}. Currency is always VND.
 */
public record TrustedSourceContext(SourceKind sourceKind, long memberId, Instant at,
                                   List<SourceLine> lines, WorkshopFact workshop, BigInteger shippingAmount) {

    public static final String CURRENCY = "VND";

    public TrustedSourceContext {
        if (sourceKind == null || memberId <= 0 || at == null) {
            throw LoyaltyException.invalidField("context", "Source kind, member and time are required.");
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
}
