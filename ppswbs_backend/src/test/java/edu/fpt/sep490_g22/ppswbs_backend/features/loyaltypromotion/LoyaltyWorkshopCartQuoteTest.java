package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReasonCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopTicketFact;
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
 * Workshop cart with several tickets (Session 2026-11-12, 003.1 FR-030/FR-033): voucher scope per ticket, minimum and
 * discount on the in-scope tickets, cap once per group, points on the whole cart after the voucher, and the in-scope
 * ticket indexes returned so 003.1 allocates the discount only to them.
 */
class LoyaltyWorkshopCartQuoteTest {

    private static final PolicyTerms POLICY = PolicyTerms.defaults();
    private static final LocalDate DAY = LocalDate.parse("2026-11-20");
    private static final Instant CREATED = Instant.parse("2026-10-01T00:00:00Z");
    private static final String S1 = "00000000-0000-4000-8000-000000000011";
    private static final String S2 = "00000000-0000-4000-8000-000000000012";
    private static final String S3 = "00000000-0000-4000-8000-000000000013";

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static WorkshopTicketFact ticket(String ref, long packageId, long price, WorkshopSessionFact... sessions) {
        return new WorkshopTicketFact(ref, packageId, n(price), List.of(sessions));
    }

    private static WorkshopSessionFact s1() {
        return WorkshopSessionFact.of(S1, 2L, DAY);
    }

    private static WorkshopSessionFact s2() {
        return WorkshopSessionFact.of(S2, 2L, DAY.plusDays(1));
    }

    /** Two tickets of package 1 (600.000 and 400.000) and one of package 2 (500.000), all on session S1. */
    private static WorkshopFact mixedCart() {
        return WorkshopFact.cart(List.of(ticket("t-a", 1L, 600_000, s1()), ticket("t-b", 1L, 400_000, s1()),
                ticket("t-c", 2L, 500_000, WorkshopSessionFact.of(S3, 3L, DAY))));
    }

    private static WorkshopCandidate voucher(DiscountTerms terms, Set<Long> packages, Set<String> sessions) {
        RetailQuote.Candidate base = new RetailQuote.Candidate(9, 90, 1, ProgramKind.VOUCHER, CREATED, terms, true,
                Set.of(), Set.of(), "WS9", null);
        return new WorkshopCandidate(base, packages, Set.of(), Set.of(), sessions);
    }

    private static DiscountTerms tenPercent(long cap, long minimum) {
        return DiscountTerms.percent(new BigDecimal("10"), n(cap), n(minimum));
    }

    private static BenefitSelection voucherSelection(long points) {
        return new BenefitSelection(SelectionChoice.VOUCHER, "WS9", n(points));
    }

    private static void assertCode(Runnable action, LoyaltyErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LoyaltyException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void onlyInScopeTicketsAreDiscountedAndListed() {
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(mixedCart(), voucherSelection(0),
                voucher(tenPercent(200_000, 0), Set.of(1L), Set.of()), POLICY, n(0), true);
        assertThat(result.grossAmount()).isEqualTo(n(1_500_000));
        assertThat(result.eligibleSubtotal()).as("tickets of package 1 only").isEqualTo(n(1_000_000));
        assertThat(result.programDiscount()).isEqualTo(n(100_000));
        assertThat(result.netBeforePoints()).isEqualTo(n(1_400_000));
        assertThat(result.eligibleLines()).containsExactly(0, 1);
    }

    @Test
    void theCapAppliesOnceToTheWholeGroup() {
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(mixedCart(), voucherSelection(0),
                voucher(tenPercent(50_000, 0), Set.of(), Set.of()), POLICY, n(0), true);
        assertThat(result.eligibleSubtotal()).isEqualTo(n(1_500_000));
        assertThat(result.programDiscount()).isEqualTo(n(50_000));
        assertThat(result.eligibleLines()).containsExactly(0, 1, 2);
    }

    @Test
    void theMinimumUsesInScopeTicketsNotTheWholeCart() {
        // Cart 1.500.000 but only 1.000.000 is in scope: a 1.200.000 minimum is not met.
        WorkshopCandidate high = voucher(tenPercent(200_000, 1_200_000), Set.of(1L), Set.of());
        assertCode(() -> WorkshopBenefitEvaluator.compute(mixedCart(), voucherSelection(0), high, POLICY, n(0), true),
                LoyaltyErrorCode.PROMOTION_INELIGIBLE);
        RetailQuote.Result preview = WorkshopBenefitEvaluator.compute(mixedCart(), voucherSelection(0), high, POLICY,
                n(0), false);
        assertThat(preview.programDiscount()).isZero();
        assertThat(preview.eligibleLines()).isEmpty();
        assertThat(preview.reasonDetails()).anyMatch(r -> r.code() == BenefitReasonCode.WORKSHOP_BELOW_MINIMUM_FEE);
    }

    @Test
    void aCartWithNoTicketInScopeIsNotCovered() {
        WorkshopCandidate other = voucher(tenPercent(200_000, 0), Set.of(7L), Set.of());
        assertCode(() -> WorkshopBenefitEvaluator.compute(mixedCart(), voucherSelection(0), other, POLICY, n(0), true),
                LoyaltyErrorCode.PROMOTION_INELIGIBLE);
        RetailQuote.Result preview = WorkshopBenefitEvaluator.compute(mixedCart(), voucherSelection(0), other, POLICY,
                n(0), false);
        assertThat(preview.reasonDetails()).anyMatch(r -> r.code() == BenefitReasonCode.WORKSHOP_NOT_COVERED);
    }

    @Test
    void everySessionOfATicketMustBeInScope() {
        // Ticket A is a two-session package (S1 + S2); ticket B is S1 only. A voucher limited to S1 covers B only.
        WorkshopFact cart = WorkshopFact.cart(List.of(ticket("t-a", 1L, 800_000, s1(), s2()),
                ticket("t-b", 1L, 300_000, s1())));
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(cart, voucherSelection(0),
                voucher(tenPercent(500_000, 0), Set.of(), Set.of(S1)), POLICY, n(0), true);
        assertThat(result.eligibleLines()).containsExactly(1);
        assertThat(result.eligibleSubtotal()).isEqualTo(n(300_000));
        assertThat(result.programDiscount()).isEqualTo(n(30_000));
    }

    @Test
    void pointsUseTheWholeCartAfterTheVoucher() {
        // 1.400.000 after the voucher, 10% cap -> 140.000 = 1.400 points.
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(mixedCart(), voucherSelection(1_400),
                voucher(tenPercent(200_000, 0), Set.of(1L), Set.of()), POLICY, n(5_000), true);
        assertThat(result.maxPoints()).isEqualTo(n(1_400));
        assertThat(result.pointDiscount()).isEqualTo(n(140_000));
        assertThat(result.earnBase()).isEqualTo(n(1_260_000));
    }

    @Test
    void aFullVoucherAndAZeroPriceTicketAreValid() {
        WorkshopFact cart = WorkshopFact.cart(List.of(ticket("t-a", 1L, 200_000, s1()), ticket("t-b", 1L, 0, s1())));
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(cart, voucherSelection(0),
                voucher(DiscountTerms.percent(new BigDecimal("100"), n(1_000_000), BigInteger.ZERO), Set.of(), Set.of()),
                POLICY, n(0), true);
        assertThat(result.programDiscount()).isEqualTo(n(200_000));
        assertThat(result.earnBase()).isZero();
        assertThat(result.eligibleLines()).containsExactly(0, 1);
    }

    @Test
    void theSinglePackageShapeKeepsNoTicketIndexes() {
        WorkshopFact single = WorkshopFact.singleSession(1L, 3, n(900_000), s1());
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(single, voucherSelection(0),
                voucher(tenPercent(200_000, 0), Set.of(1L), Set.of()), POLICY, n(0), true);
        assertThat(single.isCart()).isFalse();
        assertThat(result.programDiscount()).isEqualTo(n(90_000));
        assertThat(result.eligibleLines()).isEmpty();
    }

    @Test
    void cartSummaryFieldsFollowTheTickets() {
        WorkshopFact cart = mixedCart();
        assertThat(cart.isCart()).isTrue();
        assertThat(cart.participantCount()).isEqualTo(3);
        assertThat(cart.grossPackageAmount()).isEqualTo(n(1_500_000));
        assertThat(cart.packageId()).isEqualTo(1L);
        assertThat(cart.sessions()).extracting(WorkshopSessionFact::sessionRef).containsExactly(S1, S3);
        assertThat(cart.tickets()).extracting(WorkshopTicketFact::ticketRef).containsExactly("t-a", "t-b", "t-c");
    }

    @Test
    void invalidCartsAreRefused() {
        assertCode(() -> WorkshopFact.cart(List.of()), LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> WorkshopFact.cart(List.of(ticket("t-a", 1L, 1, s1()), ticket("t-a", 1L, 1, s1()))),
                LoyaltyErrorCode.INVALID_INPUT);
        // The same session with a different branch on another ticket.
        assertCode(() -> WorkshopFact.cart(List.of(ticket("t-a", 1L, 1, s1()),
                ticket("t-b", 1L, 1, WorkshopSessionFact.of(S1, 9L, DAY)))), LoyaltyErrorCode.INVALID_INPUT);
        // Totals that do not match the tickets.
        assertCode(() -> new WorkshopFact(1L, 1, n(999), null, List.of(ticket("t-a", 1L, 1, s1()))),
                LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> new WorkshopFact(1L, 2, n(1), null, List.of(ticket("t-a", 1L, 1, s1()))),
                LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> ticket(" ", 1L, 1, s1()), LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> ticket("t-a", 1L, -1, s1()), LoyaltyErrorCode.INVALID_INPUT);
    }
}
