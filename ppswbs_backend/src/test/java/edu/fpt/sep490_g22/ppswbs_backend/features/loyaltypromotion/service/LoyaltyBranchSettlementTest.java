package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefitPart;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SettlementPart;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopTicketFact;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Session 2026-11-12 (D-011): splitting an accepted retail checkout over its branch orders, the state of one branch
 * order, the voucher rule and the facade inputs.
 */
class LoyaltyBranchSettlementTest {

    private static final Instant NOW = Instant.parse("2026-11-12T08:00:00Z");

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static SourceLine line(long amount) {
        return new SourceLine(1L, 11L, 1, n(amount), 1L);
    }

    private static List<BigInteger> ns(long... values) {
        return java.util.Arrays.stream(values).mapToObj(BigInteger::valueOf).toList();
    }

    private static BigInteger sum(List<AcceptedBenefitPart.Share> shares,
                                  Function<AcceptedBenefitPart.Share, BigInteger> field) {
        return shares.stream().map(field).reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static void assertCode(Runnable action, LoyaltyErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LoyaltyException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    // ---- largest remainder ----

    @Test
    void largestRemainderAddsUpAndBreaksTiesByWeightThenOrder() {
        assertThat(BenefitPartAllocator.largestRemainder(n(100), ns(1, 1, 1))).containsExactly(n(34), n(33), n(33));
        assertThat(BenefitPartAllocator.largestRemainder(n(10), ns(1, 2))).containsExactly(n(3), n(7));
        // 5 over 300/200 -> 3/2 exactly; 1 over 150/150 -> the earlier part.
        assertThat(BenefitPartAllocator.largestRemainder(n(5), ns(300, 200))).containsExactly(n(3), n(2));
        assertThat(BenefitPartAllocator.largestRemainder(n(1), ns(150, 150))).containsExactly(n(1), n(0));
        assertThat(BenefitPartAllocator.largestRemainder(n(0), ns(0, 0))).containsExactly(n(0), n(0));
        assertThat(BenefitPartAllocator.largestRemainder(n(7), ns(0, 5))).containsExactly(n(0), n(7));
    }

    // ---- allocation ----

    @Test
    void theVoucherGoesOnlyToBranchOrdersWithDiscountedLinesAndEverythingAddsUp() {
        // Lines: A has 1.000.000 (discounted) + 333.333 (not); B has 666.667 (not). Voucher 100.000, 101 points,
        // point discount 10.100, 18 points earned.
        List<SourceLine> lines = List.of(line(1_000_000), line(666_667), line(333_333));
        List<SettlementPart> parts = List.of(new SettlementPart("A", List.of(0, 2)),
                new SettlementPart("B", List.of(1)));
        List<AcceptedBenefitPart.Share> shares = BenefitPartAllocator.allocate(lines, parts, List.of(0), n(100_000),
                n(101), n(10_100), n(18));

        assertThat(shares.get(0).grossAmount()).isEqualTo(n(1_333_333));
        assertThat(shares.get(0).programDiscount()).isEqualTo(n(100_000));
        assertThat(shares.get(1).programDiscount()).isZero();
        assertThat(sum(shares, AcceptedBenefitPart.Share::grossAmount)).isEqualTo(n(2_000_000));
        assertThat(sum(shares, AcceptedBenefitPart.Share::points)).isEqualTo(n(101));
        assertThat(sum(shares, AcceptedBenefitPart.Share::pointDiscount)).isEqualTo(n(10_100));
        assertThat(sum(shares, AcceptedBenefitPart.Share::earnPoints)).isEqualTo(n(18));
        assertThat(sum(shares, AcceptedBenefitPart.Share::earnBase)).isEqualTo(n(2_000_000 - 100_000 - 10_100));
        assertThat(shares).allMatch(s -> s.earnBase().signum() >= 0
                && s.grossAmount().subtract(s.programDiscount()).subtract(s.pointDiscount()).equals(s.earnBase()));
    }

    @Test
    void aZeroAmountBranchOrderGetsNothingAndNoShareGoesNegative() {
        List<SourceLine> lines = List.of(line(500_000), line(0));
        List<SettlementPart> parts = List.of(new SettlementPart("A", List.of(0)), new SettlementPart("B", List.of(1)));
        List<AcceptedBenefitPart.Share> shares = BenefitPartAllocator.allocate(lines, parts, List.of(0, 1),
                n(500_000), n(0), n(0), n(0));
        assertThat(shares.get(0).programDiscount()).isEqualTo(n(500_000));
        assertThat(shares.get(1).programDiscount()).isZero();
        assertThat(shares).allMatch(s -> s.earnBase().signum() == 0);
    }

    // ---- one branch order ----

    private static AcceptedBenefitPart part(long points, long discount) {
        return AcceptedBenefitPart.accept(1L, 1, new AcceptedBenefitPart.Share("A", n(1_000), n(discount), n(points),
                n(points), n(1_000 - discount - points), n(5)));
    }

    @Test
    void aBranchOrderIsPaidOnceOrReleasedOnceNeverBoth() {
        AcceptedBenefitPart paid = part(10, 0);
        assertThat(paid.settle("k1", NOW)).isTrue();
        assertThat(paid.settle("k1", NOW)).as("replay").isFalse();
        assertCode(() -> paid.settle("k2", NOW), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertCode(() -> paid.release("k3", NOW), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertThat(paid.grantEarn("k1")).isTrue();
        assertThat(paid.grantEarn("k1")).isFalse();
        assertThat(paid.getEarnedPoints()).isEqualTo(n(5));

        AcceptedBenefitPart released = part(10, 0);
        assertThat(released.release("r1", NOW)).isTrue();
        assertThat(released.getPointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(released.getEarnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(released.release("r1", NOW)).isFalse();
        assertCode(() -> released.settle("k1", NOW), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertCode(() -> released.grantEarn("k1"), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
    }

    @Test
    void anUnpaidBranchOrderCannotEarn() {
        assertCode(() -> part(0, 0).grantEarn("k1"), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        AcceptedBenefitPart noPoints = part(0, 0);
        assertThat(noPoints.getPointsState()).isEqualTo(ComponentState.NONE);
        assertThat(noPoints.settle("k1", NOW)).as("no points to debit").isFalse();
        assertThat(noPoints.isPaid()).isTrue();
    }

    @Test
    void theVoucherBelongsToBranchOrdersWithAShareOrToAllWhenNoneHasOne() {
        AcceptedBenefitPart withShare = part(0, 100);
        AcceptedBenefitPart without = part(0, 0);
        assertThat(LoyaltyPartLifecycleService.isVoucherPart(withShare, List.of(withShare, without))).isTrue();
        assertThat(LoyaltyPartLifecycleService.isVoucherPart(without, List.of(withShare, without))).isFalse();
        AcceptedBenefitPart other = part(0, 0);
        assertThat(LoyaltyPartLifecycleService.isVoucherPart(without, List.of(without, other))).isTrue();
    }

    // ---- facade inputs ----

    private static TrustedSourceContext retail(int lines) {
        List<SourceLine> list = java.util.stream.IntStream.range(0, lines).mapToObj(i -> line(100)).toList();
        return new TrustedSourceContext(SourceKind.RETAIL, 7L, NOW, list, null, BigInteger.ZERO);
    }

    private static AcceptedSourceContext accepted(TrustedSourceContext trusted, List<SettlementPart> parts) {
        return new AcceptedSourceContext(trusted, "ref", 1, true, NOW, NOW.plusSeconds(72 * 3600), "q", "d", parts);
    }

    @Test
    void partsMustCoverEveryLineExactlyOnce() {
        assertThat(accepted(retail(2), List.of(new SettlementPart("A", List.of(1, 0)))).parts().get(0).lineIndexes())
                .containsExactly(0, 1);
        assertCode(() -> accepted(retail(2), List.of(new SettlementPart("A", List.of(0)))),
                LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> accepted(retail(2), List.of(new SettlementPart("A", List.of(0, 1)),
                new SettlementPart("B", List.of(1)))), LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> accepted(retail(2), List.of(new SettlementPart("A", List.of(0)),
                new SettlementPart("A", List.of(1)))), LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> accepted(retail(1), List.of(new SettlementPart("A", List.of(0, 5)))),
                LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> new SettlementPart(" ", List.of(0)), LoyaltyErrorCode.INVALID_INPUT);
        assertThat(accepted(retail(1), null).parts()).isEmpty();
    }

    @Test
    void aPartOutcomeNeedsAPartReferenceAndOnlyItHasOne() {
        AuthoritativeOutcome part = new AuthoritativeOutcome(SourceKind.RETAIL, "ref", "retailorder", "k", 1,
                OutcomeKind.RETAIL_BRANCH_PAID, NOW, NOW, "s", true, "p", "A");
        assertThat(part.partRef()).isEqualTo("A");
        assertCode(() -> new AuthoritativeOutcome(SourceKind.RETAIL, "ref", "retailorder", "k", 1,
                OutcomeKind.RETAIL_BRANCH_PAID, NOW, NOW, "s", true, "p"), LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> new AuthoritativeOutcome(SourceKind.RETAIL, "ref", "retailorder", "k", 1,
                OutcomeKind.RETAIL_FULLY_PAID, NOW, NOW, "s", true, "p", "A"), LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> new AuthoritativeOutcome(SourceKind.WORKSHOP, "ref", "retailorder", "k", 1,
                OutcomeKind.RETAIL_BRANCH_UNPAID_EXPIRY, NOW, NOW, "s", true, "p", "A"), LoyaltyErrorCode.INVALID_INPUT);
    }

    // ---- workshop tickets (Session 2026-11-12, D-012) ----

    private static WorkshopTicketFact ticket(String ref, long price) {
        return new WorkshopTicketFact(ref, 1L, n(price),
                List.of(WorkshopSessionFact.of("00000000-0000-4000-8000-000000000021", 2L, java.time.LocalDate.of(2026, 11, 20))));
    }

    @Test
    void ticketSharesFollowTheRulesOf003Point1() {
        // Three 500.000 tickets, 100.000 voucher on all, 100 points at 100đ, 140 points earned for the group.
        List<AcceptedBenefitPart.Share> shares = BenefitPartAllocator.allocateTickets(
                List.of(ticket("t-1", 500_000), ticket("t-2", 500_000), ticket("t-3", 500_000)), List.of(0, 1, 2),
                n(100_000), n(100), n(100), n(130));
        assertThat(shares).extracting(AcceptedBenefitPart.Share::programDiscount)
                .containsExactly(n(33_334), n(33_333), n(33_333));
        assertThat(shares).extracting(AcceptedBenefitPart.Share::points).containsExactly(n(33), n(34), n(33));
        assertThat(shares).extracting(AcceptedBenefitPart.Share::pointDiscount)
                .containsExactly(n(3_300), n(3_400), n(3_300));
        assertThat(sum(shares, AcceptedBenefitPart.Share::earnPoints)).isEqualTo(n(130));
        assertThat(sum(shares, AcceptedBenefitPart.Share::earnBase)).isEqualTo(n(1_500_000 - 100_000 - 10_000));
    }

    @Test
    void anOutOfScopeTicketGetsNoVoucherAndAZeroTicketGetsNoPoints() {
        List<AcceptedBenefitPart.Share> shares = BenefitPartAllocator.allocateTickets(
                List.of(ticket("t-1", 300_000), ticket("t-2", 200_000), ticket("t-3", 0)), List.of(0),
                n(300_000), n(20), n(100), n(0));
        assertThat(shares.get(0).programDiscount()).isEqualTo(n(300_000));
        assertThat(shares.get(0).points()).as("nothing left to pay on ticket 1").isZero();
        assertThat(shares.get(1).points()).isEqualTo(n(20));
        assertThat(shares.get(2).points()).isZero();
        assertThat(shares).allMatch(s -> s.earnBase().signum() >= 0);
    }

    @Test
    void cappedLargestRemainderMovesWhatATicketCannotAbsorb() {
        // Weights 900/100, caps 100/1: the second share would be 10 points, it keeps 1 and the first takes 99.
        assertThat(BenefitPartAllocator.largestRemainderCapped(n(100), ns(900, 100), ns(100, 1)))
                .containsExactly(n(99), n(1));
        assertThatThrownBy(() -> BenefitPartAllocator.largestRemainderCapped(n(10), ns(1, 1), ns(2, 2)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AcceptedBenefitPart ticketPart() {
        AcceptedBenefitPart part = AcceptedBenefitPart.accept(1L, 1, new AcceptedBenefitPart.Share("t-1", n(500_000),
                n(0), n(30), n(3_000), n(497_000), n(49)));
        part.mirrorSettle(NOW);
        return part;
    }

    @Test
    void aTicketEndsOnceAsCompletedCancelledOrNoShow() {
        AcceptedBenefitPart done = ticketPart();
        assertCode(() -> done.grantTicketEarn("e1", false), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertThat(done.grantTicketEarn("e1", true)).isTrue();
        assertThat(done.grantTicketEarn("e1", true)).as("replay").isFalse();
        assertThat(done.getClosure()).isEqualTo(AcceptedBenefitPart.COMPLETED);
        assertCode(() -> done.cancelTicket("c1", AcceptedBenefitPart.VALID_CANCELLATION, NOW),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);

        AcceptedBenefitPart cancelled = ticketPart();
        assertThat(cancelled.cancelTicket("c1", AcceptedBenefitPart.VALID_CANCELLATION, NOW))
                .isEqualTo(AcceptedBenefitPart.TicketPoints.RESTORED);
        assertThat(cancelled.cancelTicket("c1", AcceptedBenefitPart.VALID_CANCELLATION, NOW))
                .isEqualTo(AcceptedBenefitPart.TicketPoints.NONE);
        assertThat(cancelled.getEarnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(cancelled.isCancelledForVoucher()).isTrue();
        assertCode(() -> cancelled.grantTicketEarn("e1", true), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);

        AcceptedBenefitPart noShow = ticketPart();
        assertThat(noShow.noShowTicket("n1", NOW)).isTrue();
        assertThat(noShow.getPointsState()).as("nothing given back").isEqualTo(ComponentState.CONSUMED);
        assertThat(noShow.isCancelledForVoucher()).isFalse();
        assertCode(() -> noShow.cancelTicket("c1", AcceptedBenefitPart.MLAND_CANCELLATION, NOW),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
    }

    @Test
    void ticketOutcomesAreWorkshopPartOutcomes() {
        AuthoritativeOutcome ok = new AuthoritativeOutcome(SourceKind.WORKSHOP, "ref", "workshopbooking", "k", 1,
                OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION, NOW, NOW, "s", true, "p", "t-1");
        assertThat(ok.outcome().isPartOutcome()).isTrue();
        assertCode(() -> new AuthoritativeOutcome(SourceKind.RETAIL, "ref", "retailorder", "k", 1,
                OutcomeKind.WORKSHOP_TICKET_NO_SHOW_FINAL, NOW, NOW, "s", true, "p", "t-1"), LoyaltyErrorCode.INVALID_INPUT);
        assertCode(() -> new AuthoritativeOutcome(SourceKind.WORKSHOP, "ref", "workshopbooking", "k", 1,
                OutcomeKind.WORKSHOP_TICKET_COMPLETED_AND_FULLY_PAID, NOW, NOW, "s", true, "p"),
                LoyaltyErrorCode.INVALID_INPUT);
    }
}
