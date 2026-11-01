package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.RetailQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.WorkshopBenefitEvaluator;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.WorkshopBenefitEvaluator.WorkshopCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T056: workshop benefit rules (FR-019-FR-022, US5.1-US5.3, US5.6-US5.8). Voucher first on the package fee, then
 * points, then FE-03 computes the deposit from the post-promotion amount; loyalty never computes or rounds the deposit.
 */
class LoyaltyWorkshopQuoteTest {

    private static final PolicyTerms POLICY = PolicyTerms.defaults();
    private static final LocalDate DAY = LocalDate.parse("2026-11-20");
    private static final Instant CREATED = Instant.parse("2026-10-01T00:00:00Z");

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static final String SESSION = "00000000-0000-4000-8000-000000000003";
    private static final String SECOND = "00000000-0000-4000-8000-000000000004";
    private static final String OTHER = "00000000-0000-4000-8000-000000000008";

    /** Package 1, one session (branch 2) on 2026-11-20, 4 participants, package fee as given. */
    private static WorkshopFact booking(long fee) {
        return WorkshopFact.singleSession(1L, 4, n(fee), WorkshopSessionFact.of(SESSION, 2L, DAY));
    }

    /** A Silverclay-like booking: one package, two sessions on two days at branch 2. */
    private static WorkshopFact twoSessions(long fee) {
        return new WorkshopFact(1L, 4, n(fee), List.of(WorkshopSessionFact.of(SECOND, 2L, DAY.plusDays(1)),
                WorkshopSessionFact.of(SESSION, 2L, DAY)));
    }

    private static WorkshopCandidate voucher(DiscountTerms terms, boolean allowPoints, Set<Long> packages,
                                             Set<Long> branches, Set<LocalDate> dates, Set<String> sessions,
                                             String unavailable) {
        RetailQuote.Candidate base = new RetailQuote.Candidate(9, 90, 1, ProgramKind.VOUCHER, CREATED, terms,
                allowPoints, Set.of(), Set.of(), "WS9", unavailable);
        return new WorkshopCandidate(base, packages, branches, dates, sessions);
    }

    private static WorkshopCandidate simple(DiscountTerms terms) {
        return voucher(terms, true, Set.of(1L), Set.of(), Set.of(), Set.of(), null);
    }

    private static DiscountTerms fixed(long amount, long cap, long minimum) {
        return DiscountTerms.fixed(n(amount), n(cap), n(minimum));
    }

    private static BenefitSelection voucherSelection(long points) {
        return new BenefitSelection(SelectionChoice.VOUCHER, "WS9", n(points));
    }

    private static void assertCode(Runnable action, LoyaltyErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LoyaltyException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void voucherThenPointsGiveThePostPromotionAmount() {
        // US5.2: fee 1.000.000, voucher 100.000, 200 points -> 880.000 (003.1 then takes 440.000 deposit).
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(200),
                simple(fixed(100_000, 100_000, 0)), POLICY, n(5_000), true);
        assertThat(result.programDiscount()).isEqualTo(n(100_000));
        assertThat(result.netBeforePoints()).isEqualTo(n(900_000));
        assertThat(result.pointDiscount()).isEqualTo(n(20_000));
        assertThat(result.earnBase()).as("post-promotion amount").isEqualTo(n(880_000));
    }

    @Test
    void thePointsCapUsesTheFeeAfterTheVoucherNotTheDeposit() {
        // US5.3: 900.000 after the voucher -> 90.000 = 900 points, further limited by available points.
        RetailQuote.Result plenty = WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(0),
                simple(fixed(100_000, 100_000, 0)), POLICY, n(5_000), false);
        assertThat(plenty.maxPoints()).isEqualTo(n(900));
        RetailQuote.Result few = WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(0),
                simple(fixed(100_000, 100_000, 0)), POLICY, n(200), false);
        assertThat(few.maxPoints()).isEqualTo(n(200));
    }

    @Test
    void theCapAppliesToTheWholeBookingNotPerParticipant() {
        // 4 participants, 10% capped at 50.000 on a 1.000.000 fee: 50.000 once, never 4 x 50.000.
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(0),
                simple(DiscountTerms.percent(new BigDecimal("10"), n(50_000), BigInteger.ZERO)), POLICY, n(0), true);
        assertThat(result.programDiscount()).isEqualTo(n(50_000));
    }

    @Test
    void anOddPostPromotionAmountStaysExact() {
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(0),
                simple(fixed(15_001, 100_000, 0)), POLICY, n(0), true);
        assertThat(result.earnBase()).isEqualTo(n(984_999));
    }

    @Test
    void everyTargetDimensionMustMatch() {
        WorkshopCandidate narrow = voucher(fixed(100_000, 100_000, 0), true, Set.of(1L), Set.of(2L), Set.of(DAY),
                Set.of(SESSION), null);
        assertThat(WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(0), narrow, POLICY, n(0), true)
                .programDiscount()).isEqualTo(n(100_000));
        for (WorkshopCandidate wrong : new WorkshopCandidate[] {
                voucher(fixed(1, 1, 0), true, Set.of(5L), Set.of(), Set.of(), Set.of(), null),
                voucher(fixed(1, 1, 0), true, Set.of(1L), Set.of(7L), Set.of(), Set.of(), null),
                voucher(fixed(1, 1, 0), true, Set.of(1L), Set.of(), Set.of(DAY.plusDays(1)), Set.of(), null),
                voucher(fixed(1, 1, 0), true, Set.of(1L), Set.of(), Set.of(), Set.of(OTHER), null)}) {
            RetailQuote.Result preview = WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(0), wrong,
                    POLICY, n(0), false);
            assertThat(preview.candidate()).isNull();
            assertThat(preview.reasons()).isNotEmpty();
            assertCode(() -> WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(0), wrong, POLICY,
                    n(0), true), LoyaltyErrorCode.PROMOTION_INELIGIBLE);
        }
    }

    @Test
    void everySelectedSessionMustSatisfyTheRestrictions() {
        // Decision 2026-10-29: a two-session package is discounted only if BOTH sessions are covered.
        WorkshopCandidate firstDayOnly = voucher(fixed(100_000, 100_000, 0), true, Set.of(1L), Set.of(), Set.of(DAY),
                Set.of(), null);
        WorkshopCandidate bothDays = voucher(fixed(100_000, 100_000, 0), true, Set.of(1L), Set.of(),
                Set.of(DAY, DAY.plusDays(1)), Set.of(), null);
        WorkshopCandidate firstSessionOnly = voucher(fixed(100_000, 100_000, 0), true, Set.of(1L), Set.of(), Set.of(),
                Set.of(SESSION), null);
        WorkshopCandidate bothSessions = voucher(fixed(100_000, 100_000, 0), true, Set.of(1L), Set.of(2L), Set.of(),
                Set.of(SESSION, SECOND), null);

        for (WorkshopCandidate partial : new WorkshopCandidate[] {firstDayOnly, firstSessionOnly}) {
            assertCode(() -> WorkshopBenefitEvaluator.compute(twoSessions(1_000_000), voucherSelection(0), partial,
                    POLICY, n(0), true), LoyaltyErrorCode.PROMOTION_INELIGIBLE);
            assertThat(WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(0), partial, POLICY, n(0),
                    true).programDiscount()).as("the same voucher fits a one-session booking").isEqualTo(n(100_000));
        }
        for (WorkshopCandidate full : new WorkshopCandidate[] {bothDays, bothSessions}) {
            assertThat(WorkshopBenefitEvaluator.compute(twoSessions(1_000_000), voucherSelection(0), full, POLICY, n(0),
                    true).programDiscount()).as("the cap still applies once per booking").isEqualTo(n(100_000));
        }
    }

    @Test
    void sessionFactsAreValidated() {
        assertThatThrownBy(() -> WorkshopSessionFact.of("3", 2L, DAY)).isInstanceOf(LoyaltyException.class);
        assertThatThrownBy(() -> new WorkshopFact(1L, 1, n(1), List.of())).isInstanceOf(LoyaltyException.class);
        assertThatThrownBy(() -> new WorkshopFact(1L, 1, n(1), List.of(WorkshopSessionFact.of(SESSION, 2L, DAY),
                WorkshopSessionFact.of(SESSION, 2L, DAY)))).isInstanceOf(LoyaltyException.class);
        assertThat(twoSessions(1).sessions()).extracting(WorkshopSessionFact::sessionRef)
                .as("stable order: date, then reference").containsExactly(SESSION, SECOND);
        assertThat(WorkshopSessionFact.of(SESSION.toUpperCase(), 2L, DAY).sessionRef()).isEqualTo(SESSION);
    }

    @Test
    void theMinimumIsThePackageFee() {
        assertCode(() -> WorkshopBenefitEvaluator.compute(booking(400_000), voucherSelection(0),
                simple(fixed(50_000, 50_000, 500_000)), POLICY, n(0), true), LoyaltyErrorCode.PROMOTION_INELIGIBLE);
        assertThat(WorkshopBenefitEvaluator.compute(booking(500_000), voucherSelection(0),
                simple(fixed(50_000, 50_000, 500_000)), POLICY, n(0), true).programDiscount()).isEqualTo(n(50_000));
    }

    @Test
    void anUnavailableVoucherOrAProductVoucherIsRefusedWithItsReason() {
        WorkshopCandidate expired = voucher(fixed(1, 1, 0), true, Set.of(1L), Set.of(), Set.of(), Set.of(),
                "This promotion has ended.");
        RetailQuote.Result preview = WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(0), expired,
                POLICY, n(0), false);
        assertThat(preview.reasons()).contains("This promotion has ended.");
        assertCode(() -> WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(0), null, POLICY, n(0),
                true), LoyaltyErrorCode.PROMOTION_INELIGIBLE);
    }

    @Test
    void thereIsNoAutomaticDiscountForWorkshops() {
        BenefitSelection auto = new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO);
        assertCode(() -> WorkshopBenefitEvaluator.compute(booking(1_000_000), auto, null, POLICY, n(0), true),
                LoyaltyErrorCode.PROMOTION_INELIGIBLE);
        assertThat(WorkshopBenefitEvaluator.compute(booking(1_000_000), auto, null, POLICY, n(0), false).reasons())
                .isNotEmpty();
    }

    @Test
    void aZeroOrNegativeFeeAfterBenefitsIsRefused() {
        // FR-022: no free booking in this version. A voucher taking the whole fee leaves 0.
        assertCode(() -> WorkshopBenefitEvaluator.compute(booking(100_000), voucherSelection(0),
                simple(fixed(100_000, 100_000, 0)), POLICY, n(0), true), LoyaltyErrorCode.WORKSHOP_NET_AMOUNT_INVALID);
        assertCode(() -> WorkshopBenefitEvaluator.compute(booking(0), new BenefitSelection(SelectionChoice.NONE, null,
                BigInteger.ZERO), null, POLICY, n(0), true), LoyaltyErrorCode.WORKSHOP_NET_AMOUNT_INVALID);
        RetailQuote.Result preview = WorkshopBenefitEvaluator.compute(booking(100_000), voucherSelection(0),
                simple(fixed(100_000, 100_000, 0)), POLICY, n(0), false);
        assertThat(preview.reasons()).isNotEmpty();
    }

    @Test
    void aVoucherThatForbidsPointsBlocksThem() {
        WorkshopCandidate noPoints = voucher(fixed(100_000, 100_000, 0), false, Set.of(1L), Set.of(), Set.of(),
                Set.of(), null);
        assertCode(() -> WorkshopBenefitEvaluator.compute(booking(1_000_000), voucherSelection(100), noPoints, POLICY,
                n(500), true), LoyaltyErrorCode.POINTS_USE_NOT_ALLOWED);
    }

    @Test
    void pointsWithoutAVoucherAreAllowed() {
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(booking(1_000_000),
                new BenefitSelection(SelectionChoice.NONE, null, n(200)), null, POLICY, n(500), true);
        assertThat(result.earnBase()).isEqualTo(n(980_000));
    }
}
