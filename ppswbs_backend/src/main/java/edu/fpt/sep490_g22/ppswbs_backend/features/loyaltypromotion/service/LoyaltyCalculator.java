package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Exact arithmetic of data-model section 3. Every result is floored to whole VND or whole points and
 * the intermediate math uses {@link BigInteger}/{@link BigDecimal} only. The workshop deposit is NOT
 * computed here: FE-03/003.1 derives it from the post-promotion amount.
 *
 * <pre>
 *   D          = min(E, cap, floor(E * percent / 100))   or   min(E, cap, fixed)
 *   MaxPoints  = min(available, floor(Net * R / (100 * V)))
 *   EarnBase   = Net - P * V
 *   Earned     = floor(EarnBase / earnUnit)
 * </pre>
 */
public final class LoyaltyCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private LoyaltyCalculator() {
    }

    /** Policy snapshot used by a calculation. */
    public record PolicyTerms(BigInteger earnUnitAmount, BigInteger redeemPointValue, BigInteger minRedeemPoints,
                              BigDecimal maxRedeemPercent) {

        public PolicyTerms {
            LoyaltyValues.requirePositiveAmount("earnUnitAmount", earnUnitAmount);
            LoyaltyValues.requirePositiveAmount("redeemPointValue", redeemPointValue);
            LoyaltyValues.requirePositiveAmount("minRedeemPoints", minRedeemPoints);
            LoyaltyValues.requirePercent("maxRedeemPercent", maxRedeemPercent);
        }

        /** Initial configuration: 10.000đ per point, 100đ per point, minimum 10 points, cap 10%. */
        public static PolicyTerms defaults() {
            return new PolicyTerms(BigInteger.valueOf(10_000), BigInteger.valueOf(100),
                    BigInteger.TEN, BigDecimal.TEN);
        }
    }

    /** Immutable discount terms of one programme revision: percent or fixed (exactly one), a cap and a minimum. */
    public record DiscountTerms(BigDecimal percent, BigInteger fixedAmount, BigInteger maxDiscount,
                                BigInteger minimumEligible) {

        public DiscountTerms {
            if ((percent == null) == (fixedAmount == null)) {
                throw LoyaltyException.invalidField("discount", "Exactly one of percent or fixed amount is required.");
            }
            if (percent != null) {
                LoyaltyValues.requirePercent("percent", percent);
            } else {
                LoyaltyValues.requirePositiveAmount("fixedAmount", fixedAmount);
            }
            LoyaltyValues.requirePositiveAmount("maxDiscount", maxDiscount);
            LoyaltyValues.requireAmount("minimumEligible", minimumEligible);
        }

        public static DiscountTerms percent(BigDecimal percent, BigInteger maxDiscount, BigInteger minimumEligible) {
            return new DiscountTerms(percent, null, maxDiscount, minimumEligible);
        }

        public static DiscountTerms fixed(BigInteger fixedAmount, BigInteger maxDiscount, BigInteger minimumEligible) {
            return new DiscountTerms(null, fixedAmount, maxDiscount, minimumEligible);
        }
    }

    /** Programme discount and whether the eligible subtotal reached the minimum. */
    public record DiscountResult(boolean eligible, BigInteger discount) {
    }

    /** Upper bound of points the customer may spend and whether any valid amount exists. */
    public record PointsLimit(BigInteger maxPoints, boolean combinable, boolean redeemable) {
    }

    /**
     * Discount for an eligible subtotal. The subtotal is the sum of in-scope purchasable lines (retail) or the
     * package fee (workshop); shipping never takes part. The discount applies once per transaction.
     */
    public static DiscountResult programDiscount(DiscountTerms terms, BigInteger eligibleSubtotal) {
        LoyaltyValues.requireAmount("eligibleSubtotal", eligibleSubtotal);
        if (eligibleSubtotal.compareTo(terms.minimumEligible()) < 0) {
            return new DiscountResult(false, BigInteger.ZERO);
        }
        BigInteger raw = terms.percent() != null
                ? new BigDecimal(eligibleSubtotal).multiply(terms.percent()).divide(HUNDRED, 0, RoundingMode.FLOOR)
                        .toBigIntegerExact()
                : terms.fixedAmount();
        BigInteger discount = raw.min(terms.maxDiscount()).min(eligibleSubtotal);
        return new DiscountResult(true, discount);
    }

    /**
     * {@code MaxPoints = min(available, floor(net * R / (100 * V)))}. When the programme forbids points the limit is
     * zero. {@code redeemable} is false below the minimum, so for example 9.900đ allows at most 9 points yet none can
     * be used.
     */
    public static PointsLimit pointsLimit(PolicyTerms policy, BigInteger netBeforePoints, BigInteger availablePoints,
                                          boolean combinable) {
        LoyaltyValues.requireAmount("netBeforePoints", netBeforePoints);
        LoyaltyValues.requireAmount("availablePoints", availablePoints);
        if (!combinable) {
            return new PointsLimit(BigInteger.ZERO, false, false);
        }
        BigInteger capByPercent = new BigDecimal(netBeforePoints).multiply(policy.maxRedeemPercent())
                .divide(HUNDRED.multiply(new BigDecimal(policy.redeemPointValue())), 0, RoundingMode.FLOOR)
                .toBigIntegerExact();
        BigInteger max = availablePoints.min(capByPercent);
        return new PointsLimit(max, true, max.compareTo(policy.minRedeemPoints()) >= 0);
    }

    /** Zero means none; otherwise the whole amount must be between the minimum and the maximum. */
    public static void requirePointsSelection(PolicyTerms policy, PointsLimit limit, BigInteger requested) {
        if (requested.signum() == 0) {
            return;
        }
        if (!limit.combinable()) {
            throw new LoyaltyException(LoyaltyErrorCode.POINTS_USE_NOT_ALLOWED, "Points cannot be used with this selection.");
        }
        if (requested.compareTo(policy.minRedeemPoints()) < 0 || requested.compareTo(limit.maxPoints()) > 0) {
            throw new LoyaltyException(LoyaltyErrorCode.POINTS_LIMIT_CHANGED,
                    "The selected points are outside the allowed range.");
        }
    }

    public static BigInteger pointDiscount(PolicyTerms policy, BigInteger points) {
        return LoyaltyValues.requireAmount("points", points).multiply(policy.redeemPointValue());
    }

    /** {@code EarnBase = NetBeforePoints - PointDiscount}; the discount can never exceed the net amount. */
    public static BigInteger earnBase(BigInteger netBeforePoints, BigInteger pointDiscount) {
        LoyaltyValues.requireAmount("netBeforePoints", netBeforePoints);
        if (pointDiscount.compareTo(netBeforePoints) > 0) {
            throw new LoyaltyException(LoyaltyErrorCode.POINTS_LIMIT_CHANGED, "Point discount exceeds the payable amount.");
        }
        return netBeforePoints.subtract(pointDiscount);
    }

    /** One grant per eligible transaction; the remainder is never carried over. */
    public static BigInteger earnedPoints(PolicyTerms policy, BigInteger earnBase) {
        LoyaltyValues.requireAmount("earnBase", earnBase);
        return earnBase.divide(policy.earnUnitAmount());
    }
}
