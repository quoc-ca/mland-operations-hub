package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.util.List;

/**
 * Read-only breakdown of a preview. It holds no quota or points and creates no accepted benefit.
 * {@code postPromotionAmount} is the workshop fee after voucher and points (retail: goods after both);
 * the workshop deposit and remaining amounts are computed by FE-03/003.1, never here.
 */
public record BenefitPreview(BigInteger grossAmount, BigInteger eligibleSubtotal, BigInteger programDiscount,
                             BigInteger netBeforePoints, BigInteger maxPoints, BigInteger minRedeemPoints,
                             BigInteger pointValue, BigInteger pointsUsed, BigInteger pointDiscount,
                             BigInteger postPromotionAmount, BigInteger earnBase,
                             Long programId, Long programRevisionNo, String normalizedCode,
                             long policyRevisionNo, boolean pointsAllowed, List<String> reasons,
                             String termsDigest) {

    public BenefitPreview {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
