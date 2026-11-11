package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReasonCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.RetailQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.RetailQuote.Candidate;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.RetailQuote.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T048: retail selection and preview rules (FR-014-FR-016, FR-025, US4.1-US4.6, US4.8). Expected values come from
 * the spec. Lines are the owner's server-priced, purchasable lines; shipping never takes part.
 */
class LoyaltyRetailQuoteTest {

    private static final PolicyTerms POLICY = PolicyTerms.defaults();
    private static final Instant EARLY = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant LATE = Instant.parse("2026-10-02T00:00:00Z");

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static SourceLine line(long product, long variant, long quantity, long unit) {
        return new SourceLine(product, variant, quantity, n(unit));
    }

    private static Candidate voucher(long id, DiscountTerms terms, boolean allowPoints, Set<Long> products) {
        return new Candidate(id, id * 10, 1, ProgramKind.VOUCHER, EARLY, terms, allowPoints, products, Set.of(),
                "CODE" + id, null);
    }

    private static Candidate auto(long id, Instant createdAt, DiscountTerms terms, Set<Long> products) {
        return new Candidate(id, id * 10, 1, ProgramKind.AUTO_DISCOUNT, createdAt, terms, true, products, Set.of(),
                null, null);
    }

    private static DiscountTerms percent(String value, long cap, long minimum) {
        return DiscountTerms.percent(new BigDecimal(value), n(cap), n(minimum));
    }

    private static DiscountTerms fixed(long amount, long cap, long minimum) {
        return DiscountTerms.fixed(n(amount), n(cap), n(minimum));
    }

    private static BenefitSelection select(SelectionChoice choice, long points) {
        return new BenefitSelection(choice, choice == SelectionChoice.VOUCHER ? "CODE1" : null, n(points));
    }

    /** 600.000 in scope (product 1), 200.000 out of scope (product 2). */
    private static final List<SourceLine> CART = List.of(line(1, 11, 2, 300_000), line(2, 21, 1, 200_000));

    @Test
    void voucherUsesOnlyEligibleLinesForThresholdAndDiscount() {
        // US4.1
        Result result = RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 0),
                voucher(1, percent("10", 50_000, 500_000), true, Set.of(1L)), List.of(), POLICY, n(0), false);
        assertThat(result.grossAmount()).isEqualTo(n(800_000));
        assertThat(result.eligibleSubtotal()).isEqualTo(n(600_000));
        assertThat(result.programDiscount()).isEqualTo(n(50_000));
        assertThat(result.netBeforePoints()).isEqualTo(n(750_000));
        assertThat(result.candidate().programId()).isEqualTo(1);
    }

    @Test
    void aThresholdAboveTheEligibleSubtotalIsNotMetEvenWhenTheCartIsLarger() {
        Result result = RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 0),
                voucher(1, percent("10", 50_000, 700_000), true, Set.of(1L)), List.of(), POLICY, n(0), false);
        assertThat(result.candidate()).isNull();
        assertThat(result.programDiscount()).isZero();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("minimum"));
        assertThatThrownBy(() -> RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 0),
                voucher(1, percent("10", 50_000, 700_000), true, Set.of(1L)), List.of(), POLICY, n(0), true))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.PROMOTION_INELIGIBLE));
    }

    @Test
    void aFixedDiscountAppliesOnceAndNeverExceedsTheEligibleSubtotal() {
        Result once = RetailQuote.compute(List.of(line(1, 11, 3, 100_000)), select(SelectionChoice.VOUCHER, 0),
                voucher(1, fixed(50_000, 50_000, 0), true, Set.of(1L)), List.of(), POLICY, n(0), false);
        assertThat(once.programDiscount()).isEqualTo(n(50_000));
        Result capped = RetailQuote.compute(List.of(line(1, 11, 1, 30_000)), select(SelectionChoice.VOUCHER, 0),
                voucher(1, fixed(50_000, 50_000, 0), true, Set.of(1L)), List.of(), POLICY, n(0), false);
        assertThat(capped.programDiscount()).isEqualTo(n(30_000));
    }

    @Test
    void variantTargetsNarrowTheEligibleLines() {
        Candidate sizeOnly = new Candidate(1, 10, 1, ProgramKind.VOUCHER, EARLY, percent("10", 1_000_000, 0), true,
                Set.of(1L), Set.of(11L), "CODE1", null);
        Result result = RetailQuote.compute(List.of(line(1, 11, 1, 100_000), line(1, 12, 1, 100_000)),
                select(SelectionChoice.VOUCHER, 0), sizeOnly, List.of(), POLICY, n(0), false);
        assertThat(result.eligibleSubtotal()).isEqualTo(n(100_000));
        assertThat(result.programDiscount()).isEqualTo(n(10_000));
    }

    private static final long BRANCH_A = 501L;
    private static final long BRANCH_B = 502L;

    private static SourceLine lineAt(long product, long variant, long unit, Long branch) {
        return new SourceLine(product, variant, 1, n(unit), branch);
    }

    private static Candidate branchVoucher(Set<Long> branches) {
        return new Candidate(1, 10, 1, ProgramKind.VOUCHER, EARLY, percent("10", 1_000_000, 0), true, Set.of(1L),
                Set.of(), branches, "CODE1", null);
    }

    @Test
    void aBranchLimitedVoucherDiscountsOnlyLinesAddedAtItsBranches() {
        // US4.11 (Session 2026-11-09): P at A and P at B, voucher 10% limited to A.
        List<SourceLine> cart = List.of(lineAt(1, 11, 1_000_000, BRANCH_A), lineAt(1, 11, 1_000_000, BRANCH_B));
        Result limited = RetailQuote.compute(cart, select(SelectionChoice.VOUCHER, 0),
                branchVoucher(Set.of(BRANCH_A)), List.of(), POLICY, n(0), false);
        assertThat(limited.eligibleSubtotal()).isEqualTo(n(1_000_000));
        assertThat(limited.programDiscount()).isEqualTo(n(100_000));
        assertThat(limited.eligibleLines()).containsExactly(0);

        Result everyBranch = RetailQuote.compute(cart, select(SelectionChoice.VOUCHER, 0),
                branchVoucher(Set.of()), List.of(), POLICY, n(0), false);
        assertThat(everyBranch.eligibleSubtotal()).isEqualTo(n(2_000_000));
        assertThat(everyBranch.eligibleLines()).containsExactly(0, 1);
    }

    @Test
    void aBranchLimitedVoucherIsRefusedWithABranchReasonWhenNoLineIsAtItsBranches() {
        List<SourceLine> onlyB = List.of(lineAt(1, 11, 1_000_000, BRANCH_B));
        Result result = RetailQuote.compute(onlyB, select(SelectionChoice.VOUCHER, 0),
                branchVoucher(Set.of(BRANCH_A)), List.of(), POLICY, n(0), false);
        assertThat(result.candidate()).isNull();
        assertThat(result.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.BRANCH_NOT_COVERED);
        assertThatThrownBy(() -> RetailQuote.compute(onlyB, select(SelectionChoice.VOUCHER, 0),
                branchVoucher(Set.of(BRANCH_A)), List.of(), POLICY, n(0), true))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.PROMOTION_INELIGIBLE));

        // A product outside the voucher stays NO_COVERED_ITEM even at the right branch.
        Result otherProduct = RetailQuote.compute(List.of(lineAt(2, 21, 100_000, BRANCH_A)),
                select(SelectionChoice.VOUCHER, 0), branchVoucher(Set.of(BRANCH_A)), List.of(), POLICY, n(0), false);
        assertThat(otherProduct.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.NO_COVERED_ITEM);
    }

    @Test
    void aLineWithoutBranchNeverMatchesABranchLimitedVoucherButStillMatchesOtherBenefits() {
        List<SourceLine> noBranch = List.of(lineAt(1, 11, 1_000_000, null));
        Result limited = RetailQuote.compute(noBranch, select(SelectionChoice.VOUCHER, 0),
                branchVoucher(Set.of(BRANCH_A)), List.of(), POLICY, n(0), false);
        assertThat(limited.candidate()).isNull();
        assertThat(limited.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.BRANCH_NOT_COVERED);

        Result unlimited = RetailQuote.compute(noBranch, select(SelectionChoice.VOUCHER, 0),
                branchVoucher(Set.of()), List.of(), POLICY, n(0), false);
        assertThat(unlimited.programDiscount()).isEqualTo(n(100_000));
        Result automatic = RetailQuote.compute(noBranch, select(SelectionChoice.AUTO, 0), null,
                List.of(auto(2, EARLY, fixed(30_000, 30_000, 0), Set.of(1L))), POLICY, n(0), false);
        assertThat(automatic.programDiscount()).isEqualTo(n(30_000));
    }

    @Test
    void onlyABranchLimitedVoucherBindsTheDiscountedLinesInTheDigest() {
        List<SourceLine> cart = List.of(lineAt(1, 11, 500_000, BRANCH_A), lineAt(1, 11, 500_000, BRANCH_B));
        List<SourceLine> swapped = List.of(lineAt(1, 11, 500_000, BRANCH_B), lineAt(1, 11, 500_000, BRANCH_A));
        Result before = RetailQuote.compute(cart, select(SelectionChoice.VOUCHER, 0),
                branchVoucher(Set.of(BRANCH_A)), List.of(), POLICY, n(0), false);
        Result after = RetailQuote.compute(swapped, select(SelectionChoice.VOUCHER, 0),
                branchVoucher(Set.of(BRANCH_A)), List.of(), POLICY, n(0), false);
        assertThat(RetailQuote.digest(before, 1, n(0))).isNotEqualTo(RetailQuote.digest(after, 1, n(0)));

        Result plain = RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 0),
                voucher(1, percent("10", 50_000, 500_000), true, Set.of(1L)), List.of(), POLICY, n(0), false);
        Result plainWithoutLines = new Result(plain.grossAmount(), plain.eligibleSubtotal(), plain.programDiscount(),
                plain.netBeforePoints(), plain.maxPoints(), plain.pointsAllowed(), plain.pointsUsed(),
                plain.pointDiscount(), plain.earnBase(), plain.candidate(), plain.reasonDetails());
        assertThat(RetailQuote.digest(plain, 1, n(0))).isEqualTo(RetailQuote.digest(plainWithoutLines, 1, n(0)));
    }

    @Test
    void theBestAutomaticDiscountWinsAndTiesGoToTheOlderProgramme() {
        List<Candidate> autos = List.of(
                auto(3, LATE, fixed(30_000, 30_000, 0), Set.of(1L)),
                auto(2, EARLY, percent("5", 30_000, 0), Set.of(1L)),
                auto(4, EARLY, fixed(10_000, 10_000, 0), Set.of(1L)));
        Result result = RetailQuote.compute(CART, select(SelectionChoice.AUTO, 0), null, autos, POLICY, n(0), false);
        assertThat(result.programDiscount()).isEqualTo(n(30_000));
        assertThat(result.candidate().programId()).as("equal discount: created first").isEqualTo(2);
    }

    @Test
    void choosingAVoucherReplacesTheAutomaticDiscount() {
        Result result = RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 0),
                voucher(1, fixed(5_000, 5_000, 0), true, Set.of(1L)),
                List.of(auto(2, EARLY, fixed(30_000, 30_000, 0), Set.of(1L))), POLICY, n(0), false);
        assertThat(result.programDiscount()).as("never stacked").isEqualTo(n(5_000));
        assertThat(result.candidate().kind()).isEqualTo(ProgramKind.VOUCHER);
    }

    @Test
    void noAutomaticDiscountAppliesWhenNoneMatches() {
        Result result = RetailQuote.compute(CART, select(SelectionChoice.AUTO, 0), null,
                List.of(auto(2, EARLY, fixed(30_000, 30_000, 0), Set.of(9L))), POLICY, n(0), false);
        assertThat(result.candidate()).isNull();
        assertThat(result.programDiscount()).isZero();
    }

    @Test
    void pointsAfterTheVoucherFollowTheExample() {
        // US4.4: 750.000 after voucher, 200 points available -> 200 points = 20.000; earn base 730.000.
        Result result = RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 200),
                voucher(1, percent("10", 50_000, 500_000), true, Set.of(1L)), List.of(), POLICY, n(200), true);
        assertThat(result.maxPoints()).isEqualTo(n(200));
        assertThat(result.pointsUsed()).isEqualTo(n(200));
        assertThat(result.pointDiscount()).isEqualTo(n(20_000));
        assertThat(result.earnBase()).isEqualTo(n(730_000));
    }

    @Test
    void pointsCanBeUsedWithoutAnyProgramme() {
        Result result = RetailQuote.compute(CART, select(SelectionChoice.NONE, 100), null, List.of(), POLICY, n(500), true);
        assertThat(result.candidate()).isNull();
        assertThat(result.maxPoints()).as("10% of 800.000 / 100").isEqualTo(n(500));
        assertThat(result.pointDiscount()).isEqualTo(n(10_000));
    }

    @Test
    void anAutomaticDiscountAlwaysAllowsPoints() {
        Result result = RetailQuote.compute(CART, select(SelectionChoice.AUTO, 50), null,
                List.of(auto(2, EARLY, fixed(30_000, 30_000, 0), Set.of(1L))), POLICY, n(100), true);
        assertThat(result.pointsAllowed()).isTrue();
        assertThat(result.pointsUsed()).isEqualTo(n(50));
    }

    @Test
    void aVoucherThatForbidsPointsBlocksThemWithAReason() {
        // US4.6
        Result preview = RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 50),
                voucher(1, percent("10", 50_000, 0), false, Set.of(1L)), List.of(), POLICY, n(100), false);
        assertThat(preview.pointsAllowed()).isFalse();
        assertThat(preview.pointsUsed()).isZero();
        assertThat(preview.reasons()).anyMatch(reason -> reason.contains("Points"));
        assertThatThrownBy(() -> RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 50),
                voucher(1, percent("10", 50_000, 0), false, Set.of(1L)), List.of(), POLICY, n(100), true))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.POINTS_USE_NOT_ALLOWED));
    }

    @Test
    void belowTheMinimumRedeemNoPointsCanBeUsed() {
        // US4.5: 9.900 -> at most 9 points, below the minimum of 10.
        Result result = RetailQuote.compute(List.of(line(1, 11, 1, 9_900)), select(SelectionChoice.NONE, 9), null,
                List.of(), POLICY, n(500), false);
        assertThat(result.maxPoints()).isEqualTo(n(9));
        assertThat(result.pointsUsed()).isZero();
        assertThat(result.reasons()).isNotEmpty();
    }

    @Test
    void anUnavailableVoucherExplainsWhy() {
        Candidate paused = new Candidate(1, 10, 1, ProgramKind.VOUCHER, EARLY, fixed(5_000, 5_000, 0), true,
                Set.of(1L), Set.of(), "CODE1", BenefitReason.of(BenefitReasonCode.PROMOTION_PAUSED));
        Result preview = RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 0), paused, List.of(), POLICY,
                n(0), false);
        assertThat(preview.candidate()).isNull();
        assertThat(preview.reasons()).contains("This promotion is paused.");
        Result missing = RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 0), null, List.of(), POLICY, n(0),
                false);
        assertThat(missing.reasons()).anyMatch(reason -> reason.contains("not found"));
        assertThat(missing.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.VOUCHER_NOT_FOUND);
    }

    @Test
    void reasonsCarryAStableCodeAndParametersBesideTheEnglishText() {
        // T081 / FR-013: the screen translates the code; the English text stays for callers that read strings.
        Result belowMinimum = RetailQuote.compute(List.of(line(1, 11, 1, 9_900)), select(SelectionChoice.NONE, 9),
                null, List.of(), POLICY, n(500), false);
        // 9.900đ allows at most 9 points, below the minimum of 10: one reason, not also "between 10 and 9" (L6).
        assertThat(belowMinimum.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.POINTS_BELOW_MINIMUM);
        assertThat(belowMinimum.reasonDetails().get(0).params()).containsEntry("min", "10");
        assertThat(belowMinimum.reasons()).hasSameSizeAs(belowMinimum.reasonDetails());

        Result outOfRange = RetailQuote.compute(List.of(line(1, 11, 1, 1_000_000)), select(SelectionChoice.NONE, 5),
                null, List.of(), POLICY, n(500), false);
        BenefitReason range = outOfRange.reasonDetails().get(0);
        assertThat(range.code()).isEqualTo(BenefitReasonCode.POINTS_OUT_OF_RANGE);
        assertThat(range.params()).containsEntry("min", "10").containsEntry("max", "500");
        assertThat(range.message()).isEqualTo("Points must be between 10 and 500.");

        Result noCombine = RetailQuote.compute(CART, select(SelectionChoice.VOUCHER, 50),
                voucher(1, percent("10", 50_000, 0), false, Set.of(1L)), List.of(), POLICY, n(100), false);
        assertThat(noCombine.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.POINTS_NOT_COMBINABLE);

        Result noAuto = RetailQuote.compute(CART, select(SelectionChoice.AUTO, 0), null, List.of(), POLICY, n(0),
                false);
        assertThat(noAuto.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.NO_AUTO_DISCOUNT);
    }

    @Test
    void theTermsDigestChangesWhenAnyAcceptedValueChanges() {
        Result a = RetailQuote.compute(CART, select(SelectionChoice.NONE, 100), null, List.of(), POLICY, n(500), true);
        Result same = RetailQuote.compute(CART, select(SelectionChoice.NONE, 100), null, List.of(), POLICY, n(500), true);
        Result more = RetailQuote.compute(CART, select(SelectionChoice.NONE, 110), null, List.of(), POLICY, n(500), true);
        assertThat(RetailQuote.digest(a, 7L, n(30_000))).isEqualTo(RetailQuote.digest(same, 7L, n(30_000)));
        assertThat(RetailQuote.digest(a, 7L, n(30_000))).isNotEqualTo(RetailQuote.digest(more, 7L, n(30_000)));
        assertThat(RetailQuote.digest(a, 7L, n(30_000))).isNotEqualTo(RetailQuote.digest(a, 8L, n(30_000)));
    }
}
