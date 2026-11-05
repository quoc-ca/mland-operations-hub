package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;

/**
 * Immutable snapshot returned by the accept transaction. For a workshop {@code postPromotionAmount}
 * is the value 003.1 uses for {@code deposit = ceil(post / 2)} and {@code remaining = post - deposit};
 * 003.1 forwards its frozen values to 003.3, so loyalty does not compute or round the deposit.
 */
public record AcceptedBenefitRef(long acceptedBenefitId, SourceKind sourceKind, String sourceRef,
                                 BigInteger grossAmount, BigInteger programDiscount, BigInteger pointsHeld,
                                 BigInteger pointDiscount, BigInteger postPromotionAmount, BigInteger earnBase,
                                 long policyRevisionNo, ComponentState pointsState, ComponentState voucherState,
                                 EarnState earnState) {
}
