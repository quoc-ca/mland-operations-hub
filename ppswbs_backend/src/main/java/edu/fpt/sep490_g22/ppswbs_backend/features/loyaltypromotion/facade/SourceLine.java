package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;

/**
 * Server-priced retail line fact supplied by the order owner (never a browser amount).
 *
 * <p>{@code locationId} (Session 2026-11-09, T085) is the branch recorded on the FE-08 cart line when the product was
 * added (the {@code locationId} of 002's FR-053 contract), not the branch that later prepares the order. It is
 * nullable so an owner that does not send it yet keeps working: such a line never matches a voucher limited to
 * branches, and is treated as before by every other benefit.
 */
public record SourceLine(long productId, long variantId, long quantity, BigInteger unitAmount, Long locationId) {

    public SourceLine {
        if (productId <= 0 || variantId <= 0 || quantity <= 0) {
            throw LoyaltyException.invalidField("lines", "Line identifiers and quantity must be positive.");
        }
        if (unitAmount == null || unitAmount.signum() < 0) {
            throw LoyaltyException.invalidField("lines", "Unit amount must not be negative.");
        }
        if (locationId != null && locationId <= 0) {
            throw LoyaltyException.invalidField("lines", "Line branch must be positive when given.");
        }
    }

    /** Shape before Session 2026-11-09 (no branch), kept so current owners keep compiling. */
    public SourceLine(long productId, long variantId, long quantity, BigInteger unitAmount) {
        this(productId, variantId, quantity, unitAmount, null);
    }

    public BigInteger lineAmount() {
        return unitAmount.multiply(BigInteger.valueOf(quantity));
    }
}
