package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;

/**
 * The share of one settlement part (Session 2026-11-12, D-011), fixed at acceptance. Over every part of a checkout
 * the shares add up exactly to the checkout totals, and no part is negative:
 * {@code grossAmount - programDiscount - pointDiscount = earnBase >= 0}. {@code pointsHeld} are the points this part
 * debits when paid and gives back when cancelled or expired unpaid; {@code earnPoints} are the points it earns when
 * paid (policy recorded at acceptance).
 * For a workshop cart (Session 2026-11-12, D-012) each part is one ticket ({@code partRef} = ticket reference) and
 * {@code closure} says how the ticket ended: {@code COMPLETED}, {@code VALID_CANCELLATION},
 * {@code MLAND_CANCELLATION} or {@code NO_SHOW}; null while open and always null for a retail branch order.
 */
public record BenefitPartRef(String partRef, BigInteger grossAmount, BigInteger programDiscount, BigInteger pointsHeld,
                             BigInteger pointDiscount, BigInteger earnBase, BigInteger earnPoints,
                             ComponentState pointsState, EarnState earnState, String closure) {
}
