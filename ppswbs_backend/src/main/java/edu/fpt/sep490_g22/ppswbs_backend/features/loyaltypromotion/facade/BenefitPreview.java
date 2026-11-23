package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.util.List;

/**
 * Read-only breakdown of a preview. It holds no quota or points and creates no accepted benefit.
 * {@code postPromotionAmount} is the workshop fee after voucher and points (retail: goods after both);
 * the workshop deposit and remaining amounts are computed by FE-03/003.1, never here.
 * {@code reasons} is the English text of each reason; {@code reasonDetails} carries the same reasons as stable codes
 * with parameters (T081) so the screen can show them in vi/en. Both lists have the same order and length when the
 * preview comes from loyalty.
 * {@code eligibleLineIndexes} (Session 2026-11-09) are the zero-based indexes of the retail source lines the chosen
 * programme discounts, so the order owner spreads the discount only over branch orders that hold those lines; empty
 * when no programme applies. For a workshop cart ({@link WorkshopFact#cart}, Session 2026-11-12) they are the
 * zero-based indexes of the in-scope tickets in cart order: 003.1 allocates {@code programDiscount} only over those
 * tickets (003.1 FR-033). Always empty for the single-package workshop shape.
 *
 * <p>Session 2026-11-22 (FR-015, FR-047, D-015): the best automatic discount always applies and at most one voucher is
 * added on top. {@code programDiscount} is the sum of both ({@code autoDiscount + voucherDiscount});
 * {@code programId}/{@code programRevisionNo}/{@code normalizedCode} name the voucher when there is one, otherwise the
 * automatic discount; {@code autoProgramId}/{@code autoProgramRevisionNo} name the automatic discount whenever one
 * applies. {@code eligibleLineIndexes} are the lines/tickets in scope of either programme. {@code lineDiscounts} gives
 * the share of each of those lines/tickets, split into the two parts: owners MUST use these shares instead of
 * splitting {@code programDiscount} themselves (empty for the single-package workshop shape, like the indexes).
 */
public record BenefitPreview(BigInteger grossAmount, BigInteger eligibleSubtotal, BigInteger programDiscount,
                             BigInteger netBeforePoints, BigInteger maxPoints, BigInteger minRedeemPoints,
                             BigInteger pointValue, BigInteger pointsUsed, BigInteger pointDiscount,
                             BigInteger postPromotionAmount, BigInteger earnBase,
                             Long programId, Long programRevisionNo, String normalizedCode,
                             long policyRevisionNo, boolean pointsAllowed, List<String> reasons,
                             String termsDigest, List<BenefitReason> reasonDetails, List<Integer> eligibleLineIndexes,
                             Long autoProgramId, Long autoProgramRevisionNo, BigInteger autoDiscount,
                             BigInteger voucherDiscount, List<LineDiscount> lineDiscounts) {

    public BenefitPreview {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        reasonDetails = reasonDetails == null ? List.of() : List.copyOf(reasonDetails);
        eligibleLineIndexes = eligibleLineIndexes == null ? List.of() : List.copyOf(eligibleLineIndexes);
        autoDiscount = autoDiscount == null ? BigInteger.ZERO : autoDiscount;
        voucherDiscount = voucherDiscount == null ? BigInteger.ZERO : voucherDiscount;
        lineDiscounts = lineDiscounts == null ? List.of() : List.copyOf(lineDiscounts);
    }

    /**
     * Shape before Session 2026-11-22 (one programme), kept so callers and their test doubles keep compiling. The
     * split is unknown here: {@code autoDiscount}/{@code voucherDiscount} are zero and {@code lineDiscounts} empty.
     */
    public BenefitPreview(BigInteger grossAmount, BigInteger eligibleSubtotal, BigInteger programDiscount,
                          BigInteger netBeforePoints, BigInteger maxPoints, BigInteger minRedeemPoints,
                          BigInteger pointValue, BigInteger pointsUsed, BigInteger pointDiscount,
                          BigInteger postPromotionAmount, BigInteger earnBase,
                          Long programId, Long programRevisionNo, String normalizedCode,
                          long policyRevisionNo, boolean pointsAllowed, List<String> reasons,
                          String termsDigest, List<BenefitReason> reasonDetails, List<Integer> eligibleLineIndexes) {
        this(grossAmount, eligibleSubtotal, programDiscount, netBeforePoints, maxPoints, minRedeemPoints, pointValue,
                pointsUsed, pointDiscount, postPromotionAmount, earnBase, programId, programRevisionNo, normalizedCode,
                policyRevisionNo, pointsAllowed, reasons, termsDigest, reasonDetails, eligibleLineIndexes,
                null, null, null, null, List.of());
    }

    /** Shape of T081 (no line indexes), kept so callers and their test doubles keep compiling. */
    public BenefitPreview(BigInteger grossAmount, BigInteger eligibleSubtotal, BigInteger programDiscount,
                          BigInteger netBeforePoints, BigInteger maxPoints, BigInteger minRedeemPoints,
                          BigInteger pointValue, BigInteger pointsUsed, BigInteger pointDiscount,
                          BigInteger postPromotionAmount, BigInteger earnBase,
                          Long programId, Long programRevisionNo, String normalizedCode,
                          long policyRevisionNo, boolean pointsAllowed, List<String> reasons,
                          String termsDigest, List<BenefitReason> reasonDetails) {
        this(grossAmount, eligibleSubtotal, programDiscount, netBeforePoints, maxPoints, minRedeemPoints, pointValue,
                pointsUsed, pointDiscount, postPromotionAmount, earnBase, programId, programRevisionNo, normalizedCode,
                policyRevisionNo, pointsAllowed, reasons, termsDigest, reasonDetails, List.of());
    }

    /** Shape before T081 (text reasons only), kept so callers and their test doubles keep compiling. */
    public BenefitPreview(BigInteger grossAmount, BigInteger eligibleSubtotal, BigInteger programDiscount,
                          BigInteger netBeforePoints, BigInteger maxPoints, BigInteger minRedeemPoints,
                          BigInteger pointValue, BigInteger pointsUsed, BigInteger pointDiscount,
                          BigInteger postPromotionAmount, BigInteger earnBase,
                          Long programId, Long programRevisionNo, String normalizedCode,
                          long policyRevisionNo, boolean pointsAllowed, List<String> reasons,
                          String termsDigest) {
        this(grossAmount, eligibleSubtotal, programDiscount, netBeforePoints, maxPoints, minRedeemPoints, pointValue,
                pointsUsed, pointDiscount, postPromotionAmount, earnBase, programId, programRevisionNo, normalizedCode,
                policyRevisionNo, pointsAllowed, reasons, termsDigest, List.of(), List.of());
    }
}
