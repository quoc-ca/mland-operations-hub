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
 *   <li>At most one automatic discount <em>or</em> one voucher per booking (FR-015). A product voucher or a
 *       voucher whose package, branch, workshop date or session does not match is refused (dimensions combine with
 *       AND). The booking window is the programme's effective window at the time of booking, separate from the
 *       workshop date.</li>
 *   <li>Workshop automatic discounts (Session 2026-11-14, FR-044) are targeted by package only and apply to every
 *       direct customer, a Guest included. Among the valid ones the largest discount wins, equal discounts go to the
 *       programme created first; a chosen voucher replaces them and the two are never added together.</li>
 *   <li>The minimum and the discount use the package fee; the cap applies once to the whole booking, never per
 *       participant.</li>
 *   <li>Cart (Session 2026-11-12, 003.1 FR-033): scope is checked per ticket; the minimum and the discount use the
 *       sum of in-scope tickets, the cap applies once to the whole group, and the result lists the in-scope ticket
 *       indexes so 003.1 allocates the discount only to them. Points use the whole cart after the voucher.</li>
 *   <li>Voucher first, then points (cap on the fee after the voucher, never on the deposit). The result is the
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
        RetailQuote.Candidate chosen = null;
        BigInteger discount = BigInteger.ZERO;
        BigInteger eligible = BigInteger.ZERO;
        List<Integer> eligibleTickets = List.of();

        if (selection.choice() == SelectionChoice.AUTO) {
            // FR-044/FR-015: the largest discount wins; equal discounts go to the programme created first, so the
            // candidates arrive in (createdAt, id) order and only a strictly larger discount replaces the choice.
            Scoped best = null;
            WorkshopCandidate bestCandidate = null;
            for (WorkshopCandidate auto : autos) {
                if (auto.base().unavailable() != null) {
                    continue;
                }
                Scoped scoped = scope(booking, auto);
                if (scoped.problem() == null && scoped.discount().signum() > 0
                        && (best == null || scoped.discount().compareTo(best.discount()) > 0)) {
                    best = scoped;
                    bestCandidate = auto;
                }
            }
            if (best == null) {
                // Never a typed failure, as for retail: the booking simply has no automatic discount.
                reasons.add(BenefitReason.of(BenefitReasonCode.WORKSHOP_NO_AUTO_DISCOUNT_APPLIES));
            } else {
                chosen = bestCandidate.base();
                eligible = best.amount();
                discount = best.discount();
                eligibleTickets = booking.isCart() ? best.tickets() : List.of();
            }
        } else if (selection.choice() == SelectionChoice.VOUCHER) {
            if (voucher == null) {
                refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                        BenefitReason.of(BenefitReasonCode.VOUCHER_NOT_FOUND));
            } else if (voucher.base().unavailable() != null) {
                refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE, voucher.base().unavailable());
            } else {
                Scoped scoped = scope(booking, voucher);
                if (scoped.problem() != null) {
                    refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                            BenefitReason.of(scoped.problem()));
                } else {
                    chosen = voucher.base();
                    eligible = scoped.amount();
                    discount = scoped.discount();
                    // The single-package shape keeps no line indexes so its existing quotes keep their digest.
                    eligibleTickets = booking.isCart() ? scoped.tickets() : List.of();
                }
            }
        }

        BigInteger net = fee.subtract(discount);
        boolean pointsAllowed = chosen == null || chosen.allowPoints();
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
                post, chosen, List.copyOf(reasons), eligibleTickets);
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

    private static Scoped scope(WorkshopFact booking, WorkshopCandidate candidate) {
        List<Integer> inScope = new ArrayList<>();
        BigInteger inScopeAmount = BigInteger.ZERO;
        List<WorkshopTicketFact> tickets = ticketsOf(booking);
        for (int i = 0; i < tickets.size(); i++) {
            if (matches(candidate, tickets.get(i))) {
                inScope.add(i);
                inScopeAmount = inScopeAmount.add(tickets.get(i).ticketPrice());
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
     */
    private static boolean matches(WorkshopCandidate voucher, WorkshopTicketFact ticket) {
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
