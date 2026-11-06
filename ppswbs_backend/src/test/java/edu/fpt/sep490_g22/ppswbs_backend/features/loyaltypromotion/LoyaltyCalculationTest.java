package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PointsLimit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Expected values come from spec US4/US5/US6 and quickstart, not from the implementation:
 * default policy 10.000đ per point, 100đ per point on redeem, minimum 10 points, cap 10%.
 */
class LoyaltyCalculationTest {

    private static final PolicyTerms POLICY = PolicyTerms.defaults();

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static DiscountTerms percent(String percent, long cap, long minimum) {
        return DiscountTerms.percent(new BigDecimal(percent), n(cap), n(minimum));
    }

    private static DiscountTerms fixed(long amount, long cap, long minimum) {
        return DiscountTerms.fixed(n(amount), n(cap), n(minimum));
    }

    // ----- programme discount (FR-014) -----

    @Test
    void thresholdAndCapUseOnlyEligibleLines() {
        // US4.1: eligible 600.000, out-of-scope 200.000, shipping 30.000; 10%, cap 50.000, minimum 500.000.
        DiscountResult result = LoyaltyCalculator.programDiscount(percent("10", 50_000, 500_000), n(600_000));
        assertThat(result.eligible()).isTrue();
        assertThat(result.discount()).isEqualTo(n(50_000));
    }

    @Test
    void thresholdHigherThanEligibleSubtotalIsRejectedEvenWhenWholeCartIsLarger() {
        // US4.1: minimum 700.000 on eligible 600.000 although the whole cart is 800.000.
        DiscountResult result = LoyaltyCalculator.programDiscount(percent("10", 50_000, 700_000), n(600_000));
        assertThat(result.eligible()).isFalse();
        assertThat(result.discount()).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void thresholdIsInclusiveAndOneDongShortFails() {
        assertThat(LoyaltyCalculator.programDiscount(percent("10", 50_000, 500_000), n(500_000)).eligible()).isTrue();
        assertThat(LoyaltyCalculator.programDiscount(percent("10", 50_000, 500_000), n(499_999)).eligible()).isFalse();
    }

    @Test
    void percentageIsFlooredNotRoundedUp() {
        // US4.2: 33.333đ at 7% is 2.333đ (2.33331).
        DiscountResult result = LoyaltyCalculator.programDiscount(percent("7", 50_000, 0), n(33_333));
        assertThat(result.discount()).isEqualTo(n(2_333));
    }

    @Test
    void fixedDiscountAppliesOnceAndNeverExceedsEligibleSubtotal() {
        assertThat(LoyaltyCalculator.programDiscount(fixed(50_000, 100_000, 0), n(600_000)).discount())
                .isEqualTo(n(50_000));
        assertThat(LoyaltyCalculator.programDiscount(fixed(50_000, 100_000, 0), n(30_000)).discount())
                .isEqualTo(n(30_000));
    }

    @Test
    void discountNeverExceedsTheCap() {
        assertThat(LoyaltyCalculator.programDiscount(fixed(80_000, 50_000, 0), n(600_000)).discount())
                .isEqualTo(n(50_000));
        assertThat(LoyaltyCalculator.programDiscount(percent("50", 20_000, 0), n(600_000)).discount())
                .isEqualTo(n(20_000));
    }

    @Test
    void fractionalPercentageStaysExact() {
        // 12.5% of 10.001 = 1250.125 -> 1250
        assertThat(LoyaltyCalculator.programDiscount(percent("12.5", 1_000_000, 0), n(10_001)).discount())
                .isEqualTo(n(1_250));
    }

    @Test
    void emptyEligibleSubtotalGivesNoDiscount() {
        DiscountResult result = LoyaltyCalculator.programDiscount(percent("10", 50_000, 0), BigInteger.ZERO);
        assertThat(result.discount()).isEqualTo(BigInteger.ZERO);
    }

    // ----- points (FR-025) -----

    @Test
    void maxPointsIsLimitedByAvailablePoints() {
        // US4.4: 750.000 after voucher, 200 available -> 200 points = 20.000đ.
        PointsLimit limit = LoyaltyCalculator.pointsLimit(POLICY, n(750_000), n(200), true);
        assertThat(limit.maxPoints()).isEqualTo(n(200));
        assertThat(limit.redeemable()).isTrue();
        assertThat(LoyaltyCalculator.pointDiscount(POLICY, n(200))).isEqualTo(n(20_000));
    }

    @Test
    void maxPointsIsLimitedByTheTenPercentCap() {
        // US5.3: workshop net 900.000 -> 90.000đ = 900 points, further limited by available points.
        assertThat(LoyaltyCalculator.pointsLimit(POLICY, n(900_000), n(5_000), true).maxPoints()).isEqualTo(n(900));
        assertThat(LoyaltyCalculator.pointsLimit(POLICY, n(900_000), n(200), true).maxPoints()).isEqualTo(n(200));
    }

    @Test
    void belowMinimumRedeemCannotUsePoints() {
        // US4.5: 9.900đ -> at most 9 points, below the minimum of 10.
        PointsLimit nine = LoyaltyCalculator.pointsLimit(POLICY, n(9_900), n(500), true);
        assertThat(nine.maxPoints()).isEqualTo(n(9));
        assertThat(nine.redeemable()).isFalse();
        PointsLimit ten = LoyaltyCalculator.pointsLimit(POLICY, n(10_000), n(500), true);
        assertThat(ten.maxPoints()).isEqualTo(n(10));
        assertThat(ten.redeemable()).isTrue();
    }

    @Test
    void notCombinableMeansNoPoints() {
        // US4.6
        PointsLimit limit = LoyaltyCalculator.pointsLimit(POLICY, n(750_000), n(200), false);
        assertThat(limit.redeemable()).isFalse();
        assertThat(limit.maxPoints()).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void zeroPointsIsAlwaysAllowedAndOneToNineIsRejected() {
        PointsLimit limit = LoyaltyCalculator.pointsLimit(POLICY, n(750_000), n(200), true);
        LoyaltyCalculator.requirePointsSelection(POLICY, limit, BigInteger.ZERO);
        LoyaltyCalculator.requirePointsSelection(POLICY, limit, n(10));
        LoyaltyCalculator.requirePointsSelection(POLICY, limit, n(200));
        for (long invalid : new long[] {1, 9, 201}) {
            assertThatThrownBy(() -> LoyaltyCalculator.requirePointsSelection(POLICY, limit, n(invalid)))
                    .isInstanceOf(LoyaltyException.class)
                    .extracting(e -> ((LoyaltyException) e).code())
                    .isEqualTo(LoyaltyErrorCode.POINTS_LIMIT_CHANGED);
        }
    }

    @Test
    void pointsNotAllowedRaisesTypedError() {
        PointsLimit limit = LoyaltyCalculator.pointsLimit(POLICY, n(750_000), n(200), false);
        assertThatThrownBy(() -> LoyaltyCalculator.requirePointsSelection(POLICY, limit, n(10)))
                .isInstanceOf(LoyaltyException.class)
                .extracting(e -> ((LoyaltyException) e).code())
                .isEqualTo(LoyaltyErrorCode.POINTS_USE_NOT_ALLOWED);
    }

    // ----- spec worked examples -----

    @Test
    void retailExampleUS4_4() {
        // goods after voucher 750.000, 200 points, shipping 30.000 -> pay 760.000, earn base 730.000 -> 73 points.
        BigInteger goodsAfterProgram = n(750_000);
        BigInteger pointDiscount = LoyaltyCalculator.pointDiscount(POLICY, n(200));
        BigInteger earnBase = LoyaltyCalculator.earnBase(goodsAfterProgram, pointDiscount);
        assertThat(earnBase).isEqualTo(n(730_000));
        assertThat(earnBase.add(n(30_000))).isEqualTo(n(760_000));
        assertThat(LoyaltyCalculator.earnedPoints(POLICY, earnBase)).isEqualTo(n(73));
    }

    @Test
    void workshopExampleUS5_2() {
        // package 1.000.000, voucher 100.000 (combinable), 200 points -> post-promotion amount 880.000, earn 88.
        BigInteger net = n(1_000_000).subtract(LoyaltyCalculator.programDiscount(fixed(100_000, 100_000, 0), n(1_000_000)).discount());
        assertThat(net).isEqualTo(n(900_000));
        BigInteger post = LoyaltyCalculator.earnBase(net, LoyaltyCalculator.pointDiscount(POLICY, n(200)));
        assertThat(post).isEqualTo(n(880_000));
        assertThat(LoyaltyCalculator.earnedPoints(POLICY, post)).isEqualTo(n(88));
    }

    @Test
    void oddPostPromotionAmountStaysExactBecauseLoyaltyNeverRoundsTheDeposit() {
        // Fixed voucher 15.001đ on a 1.000.000đ package leaves 984.999đ; FE-06 hands that exact value to 003.1.
        BigInteger net = n(1_000_000).subtract(LoyaltyCalculator.programDiscount(fixed(15_001, 100_000, 0), n(1_000_000)).discount());
        assertThat(net).isEqualTo(n(984_999));
    }

    // ----- earn (FR-029) -----

    @Test
    void earnedPointsFloorPerTransactionWithoutCarry() {
        assertThat(LoyaltyCalculator.earnedPoints(POLICY, n(9_999))).isEqualTo(BigInteger.ZERO);
        assertThat(LoyaltyCalculator.earnedPoints(POLICY, n(10_000))).isEqualTo(BigInteger.ONE);
        assertThat(LoyaltyCalculator.earnedPoints(POLICY, n(19_999))).isEqualTo(BigInteger.ONE);
    }

    // ----- policy bounds and overflow (FR-026) -----

    @Test
    void policyRejectsNonPositiveRatesAndBadPercent() {
        assertThatThrownBy(() -> new PolicyTerms(BigInteger.ZERO, n(100), n(10), new BigDecimal("10")))
                .isInstanceOf(LoyaltyException.class);
        assertThatThrownBy(() -> new PolicyTerms(n(10_000), BigInteger.ZERO, n(10), new BigDecimal("10")))
                .isInstanceOf(LoyaltyException.class);
        assertThatThrownBy(() -> new PolicyTerms(n(10_000), n(100), BigInteger.ZERO, new BigDecimal("10")))
                .isInstanceOf(LoyaltyException.class);
        assertThatThrownBy(() -> new PolicyTerms(n(10_000), n(100), n(10), BigDecimal.ZERO))
                .isInstanceOf(LoyaltyException.class);
        assertThatThrownBy(() -> new PolicyTerms(n(10_000), n(100), n(10), new BigDecimal("100.000001")))
                .isInstanceOf(LoyaltyException.class);
        new PolicyTerms(n(10_000), n(100), n(10), new BigDecimal("100"));
        new PolicyTerms(n(10_000), n(100), n(10), new BigDecimal("0.000001"));
    }

    @Test
    void amountAboveTheTechnicalMaximumIsRejected() {
        BigInteger tooLarge = new BigInteger("10000000000000000000");
        assertThatThrownBy(() -> LoyaltyCalculator.programDiscount(fixed(1, 1, 0), tooLarge))
                .isInstanceOf(LoyaltyException.class);
        assertThatThrownBy(() -> LoyaltyCalculator.earnedPoints(POLICY, tooLarge))
                .isInstanceOf(LoyaltyException.class);
    }

    @Test
    void largestSupportedAmountsCalculateExactly() {
        BigInteger max = new BigInteger("9999999999999999999");
        assertThat(LoyaltyCalculator.earnedPoints(POLICY, max)).isEqualTo(new BigInteger("999999999999999"));
        assertThat(LoyaltyCalculator.programDiscount(
                DiscountTerms.percent(new BigDecimal("100"), max, BigInteger.ZERO), max).discount())
                .isEqualTo(max);
    }
}
