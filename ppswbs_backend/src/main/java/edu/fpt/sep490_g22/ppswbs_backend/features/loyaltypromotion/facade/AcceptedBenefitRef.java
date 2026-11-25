package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.util.List;

/**
 * Immutable snapshot returned by the accept transaction. For a workshop {@code postPromotionAmount}
 * (0 or more) is the value 003.1 derives the amount collected at booking and the remaining amount from, under its own
 * rule (003.1 FR-035); 003.1 forwards its frozen values to 003.3, so loyalty does not compute or round the deposit.
 * {@code parts} (Session 2026-11-12, D-011) are the shares of each settlement part, in the order the owner sent
 * them; empty when the checkout was accepted without parts.
 * Session 2026-11-22 (FR-047): {@code programDiscount = autoDiscount + voucherDiscount}.
 */
public record AcceptedBenefitRef(long acceptedBenefitId, SourceKind sourceKind, String sourceRef,
                                 BigInteger grossAmount, BigInteger programDiscount, BigInteger pointsHeld,
                                 BigInteger pointDiscount, BigInteger postPromotionAmount, BigInteger earnBase,
                                 long policyRevisionNo, ComponentState pointsState, ComponentState voucherState,
                                 EarnState earnState, List<BenefitPartRef> parts, BigInteger autoDiscount,
                                 BigInteger voucherDiscount) {

    public AcceptedBenefitRef {
        parts = parts == null ? List.of() : List.copyOf(parts);
        autoDiscount = autoDiscount == null ? BigInteger.ZERO : autoDiscount;
        voucherDiscount = voucherDiscount == null ? BigInteger.ZERO : voucherDiscount;
    }

    /** Shape before Session 2026-11-22, kept so callers and their test doubles keep compiling (split unknown). */
    public AcceptedBenefitRef(long acceptedBenefitId, SourceKind sourceKind, String sourceRef,
                              BigInteger grossAmount, BigInteger programDiscount, BigInteger pointsHeld,
                              BigInteger pointDiscount, BigInteger postPromotionAmount, BigInteger earnBase,
                              long policyRevisionNo, ComponentState pointsState, ComponentState voucherState,
                              EarnState earnState, List<BenefitPartRef> parts) {
        this(acceptedBenefitId, sourceKind, sourceRef, grossAmount, programDiscount, pointsHeld, pointDiscount,
                postPromotionAmount, earnBase, policyRevisionNo, pointsState, voucherState, earnState, parts, null, null);
    }

    /** Shape before settlement parts, kept so callers and their test doubles keep compiling. */
    public AcceptedBenefitRef(long acceptedBenefitId, SourceKind sourceKind, String sourceRef,
                              BigInteger grossAmount, BigInteger programDiscount, BigInteger pointsHeld,
                              BigInteger pointDiscount, BigInteger postPromotionAmount, BigInteger earnBase,
                              long policyRevisionNo, ComponentState pointsState, ComponentState voucherState,
                              EarnState earnState) {
        this(acceptedBenefitId, sourceKind, sourceRef, grossAmount, programDiscount, pointsHeld, pointDiscount,
                postPromotionAmount, earnBase, policyRevisionNo, pointsState, voucherState, earnState, List.of());
    }
}
