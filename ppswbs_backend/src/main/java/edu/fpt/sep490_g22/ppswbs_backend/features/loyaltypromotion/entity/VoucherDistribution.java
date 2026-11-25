package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

/**
 * How a voucher reaches the Member (Session 2026-11-22, FR-046). Chosen at creation, never changed.
 *
 * <ul>
 *   <li>{@link #PUBLIC}: usable without saving; checkout suggests it. Saving only bookmarks it.</li>
 *   <li>{@link #CLAIM_REQUIRED}: shown in Kho Voucher with a Save button; saving holds one use of the total, and only
 *       a Member who saved it can use it. One use per Member.</li>
 * </ul>
 */
public enum VoucherDistribution {
    PUBLIC,
    CLAIM_REQUIRED
}
