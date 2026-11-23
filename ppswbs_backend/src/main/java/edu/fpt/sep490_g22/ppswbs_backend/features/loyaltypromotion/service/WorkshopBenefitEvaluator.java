package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReasonCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopTicketFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PointsLimit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure workshop benefit computation (T058, FR-019-FR-022, US5.1-US5.3, US5.6-US5.8). The workshop booking owner
 * supplies the package, every selected session (reference, branch, date), the participant count and the package fee.
 *
 * <ul>
 *   <li>The best automatic discount plus at most one voucher per booking (FR-015, Session 2026-11-22). A product voucher or a
 *       voucher whose package, branch, workshop date or session does not match is refused (dimensions combine with
 *       AND). The booking window is the programme's effective window at the time of booking, separate from the
 *       workshop date.</li>
 *   <li>Workshop automatic discounts (Session 2026-11-14, FR-044) are targeted by package only and apply to every
 *       direct customer, a Guest included. Among the valid ones the largest discount wins, equal discounts go to the
 *       programme created first; a chosen voucher is added on top of it (Session 2026-11-22).</li>
 *   <li>The minimum and the discount use the package fee; the cap applies once to the whole booking, never per
 *       participant.</li>
 *   <li>Cart (Session 2026-11-12, 003.1 FR-033): scope is checked per ticket; the minimum and the discount use the
 *       sum of in-scope tickets, the cap applies once to the whole group, and the result lists the in-scope ticket
 *       indexes so 003.1 allocates the discount only to them. Points use the whole cart after the voucher.</li>
 *   <li>Session 2026-11-22 (FR-015, FR-047): the best automatic discount always applies and is split over its
 *       in-scope tickets by price; a chosen voucher is added on top and measured on its in-scope tickets after their
 *       automatic share, then split over them by that amount. The result carries each ticket's two shares.</li>
 *   <li>Automatic discount and voucher first, then points (cap on the fee after both, never on the deposit). The result is the
 *       post-promotion amount; FE-03 derives the deposit and the remaining amount from it under its own deposit
 *       policy. Loyalty never computes or rounds a deposit.</li>
 *   <li>A post-promotion amount of exactly zero is valid (decision 2026-11-12): FE-03 confirms such a booking without
 *       the payment gateway. Only a negative amount is refused, which the calculator cannot produce.</li>
 * </ul>
 * In preview mode problems become reasons; in strict mode (accept) they are typed failures.
 */
public final class WorkshopBenefitEvaluator {

    private WorkshopBenefitEvaluator() {
    }

    /** A workshop programme with its target sets; an empty set means the dimension is not restricted. */
    public record WorkshopCandidate(RetailQuote.Candidate base, Set<Long> packages, Set<Long> branches,
                                    Set<LocalDate> workshopDates, Set<String> sessions) {

        /** A workshop automatic discount, which is targeted by package only (Session 2026-11-14, FR-044). */
        public static WorkshopCandidate auto(RetailQuote.Candidate base, Set<Long> packages) {
            return new WorkshopCandidate(base, packages, Set.of(), Set.of(), Set.of());
        }
    }

    /** Shape before workshop automatic discounts; no automatic discount applies. */
    public static RetailQuote.Result compute(WorkshopFact booking, BenefitSelection selection, WorkshopCandidate voucher,
                                             PolicyTerms policy, BigInteger availablePoints, boolean strict) {
        return compute(booking, selection, voucher, List.of(), policy, availablePoints, strict);
    }

    public static RetailQuote.Result compute(WorkshopFact booking, BenefitSelection selection, WorkshopCandidate voucher,
                                             List<WorkshopCandidate> autos, PolicyTerms policy,
                                             BigInteger availablePoints, boolean strict) {
        List<BenefitReason> reasons = new ArrayList<>();
        BigInteger fee = LoyaltyValues.requireAmount("grossPackageAmount", booking.grossPackageAmount());
        List<WorkshopTicketFact> tickets = ticketsOf(booking);
        List<BigInteger> prices = tickets.stream().map(WorkshopTicketFact::ticketPrice).toList();

        // 1. The best automatic discount, whatever the selection (FR-015, Session 2026-11-22). FR-044: the largest
        // discount wins; equal discounts go to the programme created first, so the candidates arrive in
        // (createdAt, id) order and only a strictly larger discount replaces the choice.
        Scoped bestAuto = null;
        WorkshopCandidate bestAutoCandidate = null;
        for (WorkshopCandidate auto : autos) {
            if (auto.base().unavailable() != null) {
                continue;
            }
            Scoped scoped = scope(tickets, prices, auto);
            if (scoped.problem() == null && scoped.discount().signum() > 0
                    && (bestAuto == null || scoped.discount().compareTo(bestAuto.discount()) > 0)) {
                bestAuto = scoped;
                bestAutoCandidate = auto;
            }
        }
        if (bestAuto == null && selection.choice() == SelectionChoice.AUTO) {
            // Never a typed failure, as for retail: the booking simply has no automatic discount.
            reasons.add(BenefitReason.of(BenefitReasonCode.WORKSHOP_NO_AUTO_DISCOUNT_APPLIES));
        }
        BigInteger autoDiscount = bestAuto == null ? BigInteger.ZERO : bestAuto.discount();
        List<BigInteger> ticketAuto = bestAuto == null ? RetailQuote.zeros(tickets.size())
                : RetailQuote.split(autoDiscount, inScopeWeights(prices, bestAuto.tickets()));

        // 2. The voucher on the in-scope tickets after their automatic share (FR-019, FR-047).
        Scoped voucherScoped = null;
        if (selection.choice() == SelectionChoice.VOUCHER) {
            if (voucher == null) {
                refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                        BenefitReason.of(BenefitReasonCode.VOUCHER_NOT_FOUND));
            } else if (voucher.base().unavailable() != null) {
                refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE, voucher.base().unavailable());
            } else {
                List<BigInteger> afterAuto = new ArrayList<>(tickets.size());
                for (int i = 0; i < tickets.size(); i++) {
                    afterAuto.add(prices.get(i).subtract(ticketAuto.get(i)));
                }
                Scoped scoped = scope(tickets, afterAuto, voucher);
                if (scoped.problem() != null) {
                    refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                            BenefitReason.of(scoped.problem()));
                } else if (scoped.discount().signum() == 0) {
                    // T139: nothing left after the automatic discount; never hold a use for 0 VND.
                    refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                            BenefitReason.of(BenefitReasonCode.VOUCHER_NO_EXTRA_DISCOUNT));
                } else {
                    voucherScoped = scoped;
                }
            }
        }
        BigInteger voucherDiscount = voucherScoped == null ? BigInteger.ZERO : voucherScoped.discount();
        List<BigInteger> ticketVoucher = RetailQuote.zeros(tickets.size());
        if (voucherScoped != null) {
            List<BigInteger> weights = new ArrayList<>(tickets.size());
            for (int i = 0; i < tickets.size(); i++) {
                weights.add(voucherScoped.tickets().contains(i) ? prices.get(i).subtract(ticketAuto.get(i))
                        : BigInteger.ZERO);
            }
            ticketVoucher = RetailQuote.split(voucherDiscount, weights);
        }

        RetailQuote.Candidate chosen = voucherScoped != null ? voucher.base()
                : bestAuto != null ? bestAutoCandidate.base() : null;
        BigInteger eligible = voucherScoped != null ? voucherScoped.amount()
                : bestAuto != null ? bestAuto.amount() : BigInteger.ZERO;
        BigInteger discount = autoDiscount.add(voucherDiscount);
        // The single-package shape keeps no line indexes so its existing quotes keep their digest.
        List<Integer> eligibleTickets = List.of();
        if (booking.isCart()) {
            java.util.TreeSet<Integer> union = new java.util.TreeSet<>();
            if (bestAuto != null) {
                union.addAll(bestAuto.tickets());
            }
            if (voucherScoped != null) {
                union.addAll(voucherScoped.tickets());
            }
            eligibleTickets = List.copyOf(union);
        }

        BigInteger net = fee.subtract(discount);
        // An automatic discount always allows points; a voucher may forbid them.
        boolean pointsAllowed = voucherScoped == null || voucher.base().allowPoints();
        PointsLimit limit = LoyaltyCalculator.pointsLimit(policy, net, availablePoints, pointsAllowed);
        BigInteger requested = selection.requestedPoints();
        BigInteger used = BigInteger.ZERO;
        if (requested.signum() > 0) {
            if (strict) {
                LoyaltyCalculator.requirePointsSelection(policy, limit, requested);
                used = requested;
            } else if (!pointsAllowed) {
                reasons.add(BenefitReason.of(BenefitReasonCode.POINTS_NOT_COMBINABLE));
            } else if (!limit.redeemable()) {
                // The maximum is below the minimum: one reason, never "between 10 and 9" (review 2026-11-10, L6).
                reasons.add(BenefitReason.of(BenefitReasonCode.POINTS_BELOW_MINIMUM, "min", policy.minRedeemPoints()));
            } else if (requested.compareTo(policy.minRedeemPoints()) < 0 || requested.compareTo(limit.maxPoints()) > 0) {
                reasons.add(BenefitReason.of(BenefitReasonCode.POINTS_OUT_OF_RANGE, "min", policy.minRedeemPoints(),
                        "max", limit.maxPoints()));
            } else {
                used = requested;
            }
        }
        BigInteger pointDiscount = LoyaltyCalculator.pointDiscount(policy, used);
        BigInteger post = LoyaltyCalculator.earnBase(net, pointDiscount);
        if (post.signum() < 0) {
            refuse(strict, reasons, LoyaltyErrorCode.WORKSHOP_NET_AMOUNT_INVALID,
                    BenefitReason.of(BenefitReasonCode.WORKSHOP_NET_AMOUNT_INVALID));
        }
        return new RetailQuote.Result(fee, eligible, discount, net, limit.maxPoints(), pointsAllowed, used, pointDiscount,
                post, chosen, List.copyOf(reasons), eligibleTickets, bestAuto == null ? null : bestAutoCandidate.base(),
                autoDiscount, ticketAuto, ticketVoucher);
    }

    private static List<BigInteger> inScopeWeights(List<BigInteger> prices, List<Integer> inScope) {
        List<BigInteger> weights = new ArrayList<>(prices.size());
        for (int i = 0; i < prices.size(); i++) {
            weights.add(inScope.contains(i) ? prices.get(i) : BigInteger.ZERO);
        }
        return weights;
    }

    /**
     * One programme measured against this booking: the in-scope ticket indexes, their total and the discount, or the
     * reason it does not apply. The same rule serves a voucher and an automatic discount (FR-014/FR-019): the minimum
     * and the discount use the in-scope tickets only, and the cap applies once to the whole booking.
     */
    private record Scoped(List<Integer> tickets, BigInteger amount, BigInteger discount, BenefitReasonCode problem) {

        static Scoped refused(BenefitReasonCode problem) {
            return new Scoped(List.of(), BigInteger.ZERO, BigInteger.ZERO, problem);
        }
    }

    /** {@code amounts}: each ticket's price, or (for a voucher) its price after the automatic share (FR-047). */
    private static Scoped scope(List<WorkshopTicketFact> tickets, List<BigInteger> amounts,
                                WorkshopCandidate candidate) {
        List<Integer> inScope = new ArrayList<>();
        BigInteger inScopeAmount = BigInteger.ZERO;
        for (int i = 0; i < tickets.size(); i++) {
            if (matches(candidate, tickets.get(i))) {
                inScope.add(i);
                inScopeAmount = inScopeAmount.add(amounts.get(i));
            }
        }
        if (inScope.isEmpty()) {
            return Scoped.refused(BenefitReasonCode.WORKSHOP_NOT_COVERED);
        }
        DiscountResult result = LoyaltyCalculator.programDiscount(candidate.base().terms(), inScopeAmount);
        if (!result.eligible()) {
            return Scoped.refused(BenefitReasonCode.WORKSHOP_BELOW_MINIMUM_FEE);
        }
        return new Scoped(List.copyOf(inScope), inScopeAmount, result.discount(), null);
    }

    /** The cart's tickets, or the single-package booking as one ticket priced at the whole package fee. */
    private static List<WorkshopTicketFact> ticketsOf(WorkshopFact booking) {
        return booking.isCart() ? booking.tickets()
                : List.of(new WorkshopTicketFact("package", booking.packageId(), booking.grossPackageAmount(),
                        booking.sessions()));
    }

    /**
     * The ticket's package must be targeted, and EVERY session of the ticket must satisfy the branch, date and session
     * restrictions (decision 2026-10-29): the discount covers the whole package, so a voucher limited to a day or a
     * session cannot discount a ticket whose other session falls outside it. Other tickets of a cart are judged on
     * their own (Session 2026-11-12).
     *
     * <p>Public so the per-ticket scope check of a ticket change (003.3 FR-045, BE-DESIGN-0007) applies exactly this
     * rule to the accepted revision's targets and a proposed session set; only the target sets are read.
     */
    public static boolean matches(WorkshopCandidate voucher, WorkshopTicketFact ticket) {
        return (voucher.packages().isEmpty() || voucher.packages().contains(ticket.packageId()))
                && ticket.sessions().stream().allMatch(session ->
                        (voucher.branches().isEmpty() || voucher.branches().contains(session.branchId()))
                        && (voucher.workshopDates().isEmpty() || voucher.workshopDates().contains(session.workshopDate()))
                        && (voucher.sessions().isEmpty() || voucher.sessions().contains(session.sessionRef())));
    }

    private static void refuse(boolean strict, List<BenefitReason> reasons, LoyaltyErrorCode code,
                               BenefitReason reason) {
        if (strict) {
            throw new LoyaltyException(code, reason.message());
        }
        reasons.add(reason);
    }
}
