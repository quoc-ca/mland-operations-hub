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
 *   <li>Session 2026-11-22 (FR-015, FR-047): the best automatic discount always applies (largest discount, ties go to
 *       the programme created first, then the lower ID), whatever the selection; a chosen voucher is added on top.
 *       The automatic discount is split over its in-scope lines by amount (largest remainder); the voucher's minimum,
 *       cap and discount use its in-scope lines after their automatic share, and the voucher is split over them by
 *       that remaining amount. Points come after both.</li>
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

    /**
     * {@code reasonDetails} are the preview reasons as codes (T081); {@link #reasons()} is their English text.
     *
     * <p>Session 2026-11-22 (FR-047): {@code candidate} is the voucher when one applies, otherwise the automatic
     * discount, so every existing reader keeps its meaning; {@code auto} is the automatic discount whenever one
     * applies (also alone). {@code programDiscount} is the sum of {@code autoDiscount} and the voucher's discount.
     * {@code lineAutoDiscounts}/{@code lineVoucherDiscounts} hold the share of every source line (retail lines, or
     * the tickets of a workshop booking), in line order; they add up to the two totals.
     */
    public record Result(BigInteger grossAmount, BigInteger eligibleSubtotal, BigInteger programDiscount,
                         BigInteger netBeforePoints, BigInteger maxPoints, boolean pointsAllowed, BigInteger pointsUsed,
                         BigInteger pointDiscount, BigInteger earnBase, Candidate candidate,
                         List<BenefitReason> reasonDetails, List<Integer> eligibleLines, Candidate auto,
                         BigInteger autoDiscount, List<BigInteger> lineAutoDiscounts,
                         List<BigInteger> lineVoucherDiscounts) {

        /**
         * {@code eligibleLines} (Session 2026-11-09): zero-based indexes of the source lines the applied programmes
         * discount (since Session 2026-11-22: in scope of the automatic discount or of the voucher), so the owner
         * spreads the discount only over branch orders holding them; empty without programme.
         */
        public Result {
            reasonDetails = reasonDetails == null ? List.of() : List.copyOf(reasonDetails);
            eligibleLines = eligibleLines == null ? List.of() : List.copyOf(eligibleLines);
            autoDiscount = autoDiscount == null ? BigInteger.ZERO : autoDiscount;
            lineAutoDiscounts = lineAutoDiscounts == null ? List.of() : List.copyOf(lineAutoDiscounts);
            lineVoucherDiscounts = lineVoucherDiscounts == null ? List.of() : List.copyOf(lineVoucherDiscounts);
        }

        /** Shape before Session 2026-11-22: one programme, no split. */
        public Result(BigInteger grossAmount, BigInteger eligibleSubtotal, BigInteger programDiscount,
                      BigInteger netBeforePoints, BigInteger maxPoints, boolean pointsAllowed, BigInteger pointsUsed,
                      BigInteger pointDiscount, BigInteger earnBase, Candidate candidate,
                      List<BenefitReason> reasonDetails, List<Integer> eligibleLines) {
            this(grossAmount, eligibleSubtotal, programDiscount, netBeforePoints, maxPoints, pointsAllowed, pointsUsed,
                    pointDiscount, earnBase, candidate, reasonDetails, eligibleLines,
                    candidate != null && candidate.kind() == ProgramKind.AUTO_DISCOUNT ? candidate : null,
                    candidate != null && candidate.kind() == ProgramKind.AUTO_DISCOUNT ? programDiscount : null,
                    List.of(), List.of());
        }

        /** The voucher applied on top of the automatic discount, or {@code null}. */
        public Candidate voucher() {
            return candidate != null && candidate.kind() == ProgramKind.VOUCHER ? candidate : null;
        }

        public BigInteger voucherDiscount() {
            return programDiscount.subtract(autoDiscount);
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

        // 1. The best automatic discount, whatever the selection (FR-015): it cannot be declined.
        Candidate bestAuto = null;
        BigInteger autoEligible = BigInteger.ZERO;
        BigInteger autoDiscount = BigInteger.ZERO;
        Comparator<Candidate> order = Comparator.comparing(Candidate::createdAt).thenComparing(Candidate::programId);
        for (Candidate auto : autos.stream().sorted(order).toList()) {
            if (auto.unavailable() != null) {
                continue;
            }
            BigInteger subtotal = eligibleSubtotal(lines, auto);
            DiscountResult result = LoyaltyCalculator.programDiscount(auto.terms(), subtotal);
            if (result.eligible() && subtotal.signum() > 0 && result.discount().compareTo(autoDiscount) > 0) {
                bestAuto = auto;
                autoEligible = subtotal;
                autoDiscount = result.discount();
            }
        }
        if (bestAuto == null && selection.choice() == SelectionChoice.AUTO) {
            reasons.add(BenefitReason.of(BenefitReasonCode.NO_AUTO_DISCOUNT));
        }
        Candidate autoChosen = bestAuto;
        List<BigInteger> lineAuto = splitByLineKey(autoDiscount, lines.stream()
                .map(line -> autoChosen != null && matches(line, autoChosen) ? line.lineAmount() : BigInteger.ZERO)
                .toList(), lines);

        // 2. The voucher on what the automatic discount left of its in-scope lines (FR-014, FR-047).
        Candidate voucherChosen = null;
        BigInteger voucherEligible = BigInteger.ZERO;
        BigInteger voucherDiscount = BigInteger.ZERO;
        List<BigInteger> voucherBase = new ArrayList<>(lines.size());
        if (selection.choice() == SelectionChoice.VOUCHER) {
            if (voucher == null) {
                ineligible(strict, reasons, BenefitReason.of(BenefitReasonCode.VOUCHER_NOT_FOUND));
            } else if (voucher.unavailable() != null) {
                ineligible(strict, reasons, voucher.unavailable());
            } else {
                boolean covered = false;
                for (int i = 0; i < lines.size(); i++) {
                    boolean inScope = matches(lines.get(i), voucher);
                    covered |= inScope && lines.get(i).lineAmount().signum() > 0;
                    voucherBase.add(inScope ? lines.get(i).lineAmount().subtract(lineAuto.get(i)) : BigInteger.ZERO);
                }
                BigInteger subtotal = voucherBase.stream().reduce(BigInteger.ZERO, BigInteger::add);
                DiscountResult result = LoyaltyCalculator.programDiscount(voucher.terms(), subtotal);
                if (!covered) {
                    // Items are covered but none was added at a branch of the voucher (Session 2026-11-09).
                    boolean branchOnly = !voucher.branchTargets().isEmpty()
                            && lines.stream().anyMatch(line -> matchesItem(line, voucher));
                    ineligible(strict, reasons, BenefitReason.of(branchOnly ? BenefitReasonCode.BRANCH_NOT_COVERED
                            : BenefitReasonCode.NO_COVERED_ITEM));
                } else if (!result.eligible()) {
                    ineligible(strict, reasons, BenefitReason.of(BenefitReasonCode.BELOW_MINIMUM_AMOUNT));
                } else if (result.discount().signum() == 0) {
                    // T139: nothing left after the automatic discount; never hold a use for 0 VND.
                    ineligible(strict, reasons, BenefitReason.of(BenefitReasonCode.VOUCHER_NO_EXTRA_DISCOUNT));
                } else {
                    voucherChosen = voucher;
                    voucherEligible = subtotal;
                    voucherDiscount = result.discount();
                }
            }
        }
        List<BigInteger> lineVoucher = voucherChosen == null ? zeros(lines.size())
                : splitByLineKey(voucherDiscount, voucherBase, lines);

        Candidate chosen = voucherChosen != null ? voucherChosen : bestAuto;
        BigInteger eligible = voucherChosen != null ? voucherEligible : autoEligible;
        BigInteger discount = autoDiscount.add(voucherDiscount);
        BigInteger net = gross.subtract(discount);
        // An automatic discount always allows points; a voucher may forbid them.
        boolean pointsAllowed = voucherChosen == null || voucherChosen.allowPoints();
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
        for (int i = 0; i < lines.size(); i++) {
            SourceLine line = lines.get(i);
            if ((bestAuto != null && matches(line, bestAuto)) || (voucherChosen != null && matches(line, voucherChosen))) {
                eligibleLines.add(i);
            }
        }
        return new Result(gross, eligible, discount, net, limit.maxPoints(), pointsAllowed, used, pointDiscount,
                earnBase, chosen, List.copyOf(reasons), eligibleLines, bestAuto, autoDiscount, lineAuto, lineVoucher);
    }

    /** {@code total} over {@code weights} by largest remainder (FR-047), or zeros when there is nothing to split. */
    static List<BigInteger> split(BigInteger total, List<BigInteger> weights) {
        return total.signum() == 0 ? zeros(weights.size()) : BenefitPartAllocator.largestRemainder(total, weights);
    }

    static List<BigInteger> zeros(int size) {
        return java.util.Collections.nCopies(size, BigInteger.ZERO);
    }

    /**
     * {@link #split} over retail lines taken in {@link #lineKey} order, the result back in the owner's line order
     * (BUG-INT-0003): the owner may send the same lines in another order at accept than at preview, so a tie in the
     * largest remainder goes to the smaller line key, not to whichever line came first.
     */
    static List<BigInteger> splitByLineKey(BigInteger total, List<BigInteger> weights, List<SourceLine> lines) {
        List<Integer> order = java.util.stream.IntStream.range(0, lines.size()).boxed()
                .sorted(Comparator.comparing(i -> lineKey(lines.get(i)), LINE_KEY_ORDER)).toList();
        List<BigInteger> sorted = split(total, order.stream().map(weights::get).toList());
        BigInteger[] shares = new BigInteger[lines.size()];
        for (int k = 0; k < order.size(); k++) {
            shares[order.get(k)] = sorted.get(k);
        }
        return List.of(shares);
    }

    /** What identifies a retail line whatever its position: product, size, branch, quantity and unit price. */
    private record LineKey(long productId, long variantId, long locationId, long quantity, BigInteger unitAmount) {
        @Override
        public String toString() {
            return productId + "/" + variantId + "@" + locationId + "x" + quantity + "*" + unitAmount;
        }
    }

    private static final Comparator<LineKey> LINE_KEY_ORDER = Comparator.comparingLong(LineKey::productId)
            .thenComparingLong(LineKey::variantId).thenComparingLong(LineKey::locationId)
            .thenComparingLong(LineKey::quantity).thenComparing(LineKey::unitAmount);

    private static LineKey lineKey(SourceLine line) {
        return new LineKey(line.productId(), line.variantId(), line.locationId() == null ? 0 : line.locationId(),
                line.quantity(), line.unitAmount());
    }

    /**
     * Retail digest (BUG-INT-0003): like {@link #digest(Result, long, BigInteger)}, but the lines a branch-limited
     * voucher discounts and the per-line split of a voucher on top of an automatic discount are bound by
     * {@link #lineKey}, sorted, instead of by position, so the same lines in another order give the same digest while
     * a line moved to another branch still changes it.
     */
    public static String digest(Result result, List<SourceLine> lines, long policyId, BigInteger shippingAmount) {
        StringBuilder canonical = new StringBuilder(base(result, policyId, shippingAmount));
        Candidate chosen = result.candidate();
        if (chosen != null && !chosen.branchTargets().isEmpty()) {
            canonical.append("|lines=").append(result.eligibleLines().stream().map(i -> lineKey(lines.get(i)))
                    .sorted(LINE_KEY_ORDER).toList());
        }
        if (result.voucher() != null && result.auto() != null) {
            List<String> shares = java.util.stream.IntStream.range(0, lines.size()).boxed()
                    .sorted(Comparator.comparing(i -> lineKey(lines.get(i)), LINE_KEY_ORDER))
                    .map(i -> lineKey(lines.get(i)) + ":" + result.lineAutoDiscounts().get(i) + "/"
                            + result.lineVoucherDiscounts().get(i))
                    .toList();
            canonical.append("|auto=").append(result.auto().programId()).append('/').append(result.auto().revisionNo())
                    .append("|autoDiscount=").append(result.autoDiscount()).append("|lineShares=").append(shares);
        }
        return sha256(canonical.toString());
    }

    private static String base(Result result, long policyId, BigInteger shippingAmount) {
        Candidate chosen = result.candidate();
        return String.join("|",
                "policy=" + policyId,
                "program=" + (chosen == null ? "-" : chosen.programId() + "/" + chosen.revisionNo()),
                "gross=" + result.grossAmount(),
                "eligible=" + result.eligibleSubtotal(),
                "discount=" + result.programDiscount(),
                "points=" + result.pointsUsed(),
                "pointDiscount=" + result.pointDiscount(),
                "earnBase=" + result.earnBase(),
                "shipping=" + shippingAmount);
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * SHA-256 (hex) of every value the customer agrees to; the accept transaction must reproduce it exactly. Lines are
     * bound by position, which is right for workshop tickets (the owner keeps cart order); retail uses
     * {@link #digest(Result, List, long, BigInteger)}.
     */
    public static String digest(Result result, long policyId, BigInteger shippingAmount) {
        Candidate chosen = result.candidate();
        String canonical = base(result, policyId, shippingAmount);
        if (chosen != null && !chosen.branchTargets().isEmpty()) {
            // Only branch-limited vouchers bind which lines are discounted, so existing quotes keep their digest.
            canonical = canonical + "|lines=" + result.eligibleLines();
        }
        if (result.voucher() != null && result.auto() != null) {
            // Session 2026-11-22: a voucher on top of an automatic discount binds both programmes and the split;
            // a quote with one programme keeps its digest.
            canonical = canonical + "|auto=" + result.auto().programId() + "/" + result.auto().revisionNo()
                    + "|autoDiscount=" + result.autoDiscount()
                    + "|lineAuto=" + result.lineAutoDiscounts() + "|lineVoucher=" + result.lineVoucherDiscounts();
        }
        return sha256(canonical);
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
