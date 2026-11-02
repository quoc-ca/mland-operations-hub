package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
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
 *   <li>At most one voucher per booking; there is no automatic discount for workshops. A product voucher or a
 *       voucher whose package, branch, workshop date or session does not match is refused (dimensions combine with
 *       AND). The booking window is the programme's effective window at the time of booking, separate from the
 *       workshop date.</li>
 *   <li>The minimum and the discount use the package fee; the cap applies once to the whole booking, never per
 *       participant.</li>
 *   <li>Voucher first, then points (cap on the fee after the voucher, never on the deposit). The result is the
 *       post-promotion amount, which FE-03/003.1 uses for {@code deposit = ceil(post / 2)} and the remaining amount;
 *       loyalty never computes or rounds a deposit.</li>
 *   <li>A post-promotion amount of zero or less is refused in this version: no free booking.</li>
 * </ul>
 * In preview mode problems become reasons; in strict mode (accept) they are typed failures.
 */
public final class WorkshopBenefitEvaluator {

    private WorkshopBenefitEvaluator() {
    }

    /** A workshop voucher with its target sets; an empty set means the dimension is not restricted. */
    public record WorkshopCandidate(RetailQuote.Candidate base, Set<Long> packages, Set<Long> branches,
                                    Set<LocalDate> workshopDates, Set<String> sessions) {
    }

    public static RetailQuote.Result compute(WorkshopFact booking, BenefitSelection selection, WorkshopCandidate voucher,
                                             PolicyTerms policy, BigInteger availablePoints, boolean strict) {
        List<String> reasons = new ArrayList<>();
        BigInteger fee = LoyaltyValues.requireAmount("grossPackageAmount", booking.grossPackageAmount());
        RetailQuote.Candidate chosen = null;
        BigInteger discount = BigInteger.ZERO;
        BigInteger eligible = BigInteger.ZERO;

        if (selection.choice() == SelectionChoice.AUTO) {
            refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                    "Workshops have no automatic discount; enter a voucher code instead.");
        } else if (selection.choice() == SelectionChoice.VOUCHER) {
            if (voucher == null) {
                refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE, "The voucher code was not found.");
            } else if (voucher.base().unavailableReason() != null) {
                refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE, voucher.base().unavailableReason());
            } else if (!matches(voucher, booking)) {
                refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                        "This voucher does not cover this package, branch, date or session.");
            } else {
                DiscountResult result = LoyaltyCalculator.programDiscount(voucher.base().terms(), fee);
                if (!result.eligible()) {
                    refuse(strict, reasons, LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                            "The package fee does not reach the minimum amount of this voucher.");
                } else {
                    chosen = voucher.base();
                    eligible = fee;
                    discount = result.discount();
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
                reasons.add("Points cannot be used with this voucher.");
            } else if (requested.compareTo(policy.minRedeemPoints()) < 0 || requested.compareTo(limit.maxPoints()) > 0) {
                reasons.add("Points must be between " + policy.minRedeemPoints() + " and " + limit.maxPoints() + ".");
            } else {
                used = requested;
            }
        }
        BigInteger pointDiscount = LoyaltyCalculator.pointDiscount(policy, used);
        BigInteger post = LoyaltyCalculator.earnBase(net, pointDiscount);
        if (post.signum() <= 0) {
            refuse(strict, reasons, LoyaltyErrorCode.WORKSHOP_NET_AMOUNT_INVALID,
                    "The amount to pay after discounts must be greater than zero.");
        }
        return new RetailQuote.Result(fee, eligible, discount, net, limit.maxPoints(), pointsAllowed, used, pointDiscount,
                post, chosen, List.copyOf(reasons));
    }

    /**
     * The package must be targeted, and EVERY selected session must satisfy the branch, date and session restrictions
     * (decision 2026-10-29): the discount covers the whole package, so a voucher limited to a day or a session cannot
     * discount a booking whose other session falls outside it.
     */
    private static boolean matches(WorkshopCandidate voucher, WorkshopFact booking) {
        return (voucher.packages().isEmpty() || voucher.packages().contains(booking.packageId()))
                && booking.sessions().stream().allMatch(session ->
                        (voucher.branches().isEmpty() || voucher.branches().contains(session.branchId()))
                        && (voucher.workshopDates().isEmpty() || voucher.workshopDates().contains(session.workshopDate()))
                        && (voucher.sessions().isEmpty() || voucher.sessions().contains(session.sessionRef())));
    }

    private static void refuse(boolean strict, List<String> reasons, LoyaltyErrorCode code, String reason) {
        if (strict) {
            throw new LoyaltyException(code, reason);
        }
        reasons.add(reason);
    }
}
