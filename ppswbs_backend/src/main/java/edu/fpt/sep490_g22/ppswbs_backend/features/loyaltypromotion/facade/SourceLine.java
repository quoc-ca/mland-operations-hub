package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;

/** Server-priced retail line fact supplied by the order owner (never a browser amount). */
public record SourceLine(long productId, long variantId, long quantity, BigInteger unitAmount) {

    public SourceLine {
        if (productId <= 0 || variantId <= 0 || quantity <= 0) {
            throw LoyaltyException.invalidField("lines", "Line identifiers and quantity must be positive.");
        }
        if (unitAmount == null || unitAmount.signum() < 0) {
            throw LoyaltyException.invalidField("lines", "Unit amount must not be negative.");
        }
    }

    public BigInteger lineAmount() {
        return unitAmount.multiply(BigInteger.valueOf(quantity));
    }
}
