package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReasonCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PointsLimit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Pure retail benefit computation (T050, FR-014-FR-016, FR-025). No database access, so the same rules serve the
 * read-only preview and the re-check under locks at accept.
 *
 * <ul>
 *   <li>Lines are the owner's server-priced lines. The owner sends only lines it can sell (hidden, stopped or deleted
 *       products never reach checkout), so purchasability is the owner's guard (interfaces "Target port").</li>
 *   <li>A line is in scope when it matches every dimension the programme uses (products AND sizes). The minimum and the
 *       discount use only in-scope lines; shipping never takes part.</li>
 *   <li>One programme per order: a chosen voucher replaces automatic discounts; among automatic discounts the largest
 *       discount wins, ties go to the programme created first, then the lower ID.</li>
 *   <li>Points cap: {@code 10%} (policy) of the goods total after the programme, before points; an automatic discount
 *       or no programme always allows points, a voucher may forbid them.</li>
 * </ul>
 * In preview mode a problem becomes a reason and the affected benefit is left out; in strict mode (accept) it is a
 * typed failure, so the order is never accepted with less than the customer agreed to.
 */
public final class RetailQuote {

    private RetailQuote() {
    }

    /**
     * A programme as it applies now. {@code unavailable} is set when it cannot be used (paused, not active, no uses
     * left, wrong scope); it is then never applied.
     */
    public record Candidate(long programId, long revisionId, long revisionNo, ProgramKind kind, Instant createdAt,
                            DiscountTerms terms, boolean allowPoints, Set<Long> productTargets, Set<Long> variantTargets,
                            Set<Long> branchTargets, String normalizedCode, BenefitReason unavailable) {

        /**
         * {@code branchTargets} (Session 2026-11-09): branches a product voucher is limited to, matched against each
         * line's {@code locationId}; empty = every branch.
         */
        public Candidate {
            branchTargets = branchTargets == null ? Set.of() : Set.copyOf(branchTargets);
        }

        /** Shape before Session 2026-11-09 (no branch limit). */
        public Candidate(long programId, long revisionId, long revisionNo, ProgramKind kind, Instant createdAt,
                         DiscountTerms terms, boolean allowPoints, Set<Long> productTargets, Set<Long> variantTargets,
                         String normalizedCode, BenefitReason unavailable) {
            this(programId, revisionId, revisionNo, kind, createdAt, terms, allowPoints, productTargets,
                    variantTargets, Set.of(), normalizedCode, unavailable);
        }

        /** English text of {@link #unavailable()}, or {@code null} when the programme can be used. */
        public String unavailableReason() {
            return unavailable == null ? null : unavailable.message();
        }
    }

    /** {@code reasonDetails} are the preview reasons as codes (T081); {@link #reasons()} is their English text. */
    public record Result(BigInteger grossAmount, BigInteger eligibleSubtotal, BigInteger programDiscount,
                         BigInteger netBeforePoints, BigInteger maxPoints, boolean pointsAllowed, BigInteger pointsUsed,
                         BigInteger pointDiscount, BigInteger earnBase, Candidate candidate,
                         List<BenefitReason> reasonDetails, List<Integer> eligibleLines) {

        /**
         * {@code eligibleLines} (Session 2026-11-09): zero-based indexes of the source lines the chosen programme
         * discounts, so the owner spreads the discount only over branch orders holding them; empty without programme.
         */
        public Result {
            reasonDetails = reasonDetails == null ? List.of() : List.copyOf(reasonDetails);
            eligibleLines = eligibleLines == null ? List.of() : List.copyOf(eligibleLines);
        }

        public Result(BigInteger grossAmount, BigInteger eligibleSubtotal, BigInteger programDiscount,
                      BigInteger netBeforePoints, BigInteger maxPoints, boolean pointsAllowed, BigInteger pointsUsed,
                      BigInteger pointDiscount, BigInteger earnBase, Candidate candidate,
                      List<BenefitReason> reasonDetails) {
            this(grossAmount, eligibleSubtotal, programDiscount, netBeforePoints, maxPoints, pointsAllowed, pointsUsed,
                    pointDiscount, earnBase, candidate, reasonDetails, List.of());
        }

        public List<String> reasons() {
            return reasonDetails.stream().map(BenefitReason::message).toList();
        }
    }

    public static Result compute(List<SourceLine> lines, BenefitSelection selection, Candidate voucher,
                                 List<Candidate> autos, PolicyTerms policy, BigInteger availablePoints, boolean strict) {
        List<BenefitReason> reasons = new ArrayList<>();
        BigInteger gross = lines.stream().map(SourceLine::lineAmount).reduce(BigInteger.ZERO, BigInteger::add);
        LoyaltyValues.requireAmount("grossAmount", gross);

        Candidate chosen = null;
        BigInteger eligible = BigInteger.ZERO;
        BigInteger discount = BigInteger.ZERO;
        if (selection.choice() == SelectionChoice.VOUCHER) {
            if (voucher == null) {
                ineligible(strict, reasons, BenefitReason.of(BenefitReasonCode.VOUCHER_NOT_FOUND));
            } else if (voucher.unavailable() != null) {
                ineligible(strict, reasons, voucher.unavailable());
            } else {
                BigInteger subtotal = eligibleSubtotal(lines, voucher);
                DiscountResult result = LoyaltyCalculator.programDiscount(voucher.terms(), subtotal);
                if (subtotal.signum() == 0) {
                    // Items are covered but none was added at a branch of the voucher (Session 2026-11-09).
                    boolean branchOnly = !voucher.branchTargets().isEmpty()
                            && lines.stream().anyMatch(line -> matchesItem(line, voucher));
                    ineligible(strict, reasons, BenefitReason.of(branchOnly ? BenefitReasonCode.BRANCH_NOT_COVERED
                            : BenefitReasonCode.NO_COVERED_ITEM));
                } else if (!result.eligible()) {
                    ineligible(strict, reasons, BenefitReason.of(BenefitReasonCode.BELOW_MINIMUM_AMOUNT));
                } else {
                    chosen = voucher;
                    eligible = subtotal;
                    discount = result.discount();
                }
            }
        } else if (selection.choice() == SelectionChoice.AUTO) {
            Comparator<Candidate> order = Comparator.comparing(Candidate::createdAt).thenComparing(Candidate::programId);
            for (Candidate auto : autos.stream().sorted(order).toList()) {
                if (auto.unavailable() != null) {
                    continue;
                }
                BigInteger subtotal = eligibleSubtotal(lines, auto);
                DiscountResult result = LoyaltyCalculator.programDiscount(auto.terms(), subtotal);
                if (result.eligible() && subtotal.signum() > 0 && result.discount().compareTo(discount) > 0) {
                    chosen = auto;
                    eligible = subtotal;
                    discount = result.discount();
                }
            }
            if (chosen == null) {
                reasons.add(BenefitReason.of(BenefitReasonCode.NO_AUTO_DISCOUNT));
            }
        }

        BigInteger net = gross.subtract(discount);
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
        BigInteger earnBase = LoyaltyCalculator.earnBase(net, pointDiscount);
        List<Integer> eligibleLines = new ArrayList<>();
        if (chosen != null) {
            for (int i = 0; i < lines.size(); i++) {
                if (matches(lines.get(i), chosen)) {
                    eligibleLines.add(i);
                }
            }
        }
        return new Result(gross, eligible, discount, net, limit.maxPoints(), pointsAllowed, used, pointDiscount,
                earnBase, chosen, List.copyOf(reasons), eligibleLines);
    }

    /** SHA-256 (hex) of every value the customer agrees to; the accept transaction must reproduce it exactly. */
    public static String digest(Result result, long policyId, BigInteger shippingAmount) {
        Candidate chosen = result.candidate();
        String canonical = String.join("|",
                "policy=" + policyId,
                "program=" + (chosen == null ? "-" : chosen.programId() + "/" + chosen.revisionNo()),
                "gross=" + result.grossAmount(),
                "eligible=" + result.eligibleSubtotal(),
                "discount=" + result.programDiscount(),
                "points=" + result.pointsUsed(),
                "pointDiscount=" + result.pointDiscount(),
                "earnBase=" + result.earnBase(),
                "shipping=" + shippingAmount);
        if (chosen != null && !chosen.branchTargets().isEmpty()) {
            // Only branch-limited vouchers bind which lines are discounted, so existing quotes keep their digest.
            canonical = canonical + "|lines=" + result.eligibleLines();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static BigInteger eligibleSubtotal(List<SourceLine> lines, Candidate candidate) {
        return lines.stream()
                .filter(line -> matches(line, candidate))
                .map(SourceLine::lineAmount)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    /** Products AND sizes AND, when the voucher is limited, the branch recorded on the line (Session 2026-11-09). */
    private static boolean matches(SourceLine line, Candidate candidate) {
        return matchesItem(line, candidate) && (candidate.branchTargets().isEmpty()
                || (line.locationId() != null && candidate.branchTargets().contains(line.locationId())));
    }

    private static boolean matchesItem(SourceLine line, Candidate candidate) {
        return (candidate.productTargets().isEmpty() || candidate.productTargets().contains(line.productId()))
                && (candidate.variantTargets().isEmpty() || candidate.variantTargets().contains(line.variantId()));
    }

    private static void ineligible(boolean strict, List<BenefitReason> reasons, BenefitReason reason) {
        if (strict) {
            throw new LoyaltyException(LoyaltyErrorCode.PROMOTION_INELIGIBLE, reason.message());
        }
        reasons.add(reason);
    }
}
