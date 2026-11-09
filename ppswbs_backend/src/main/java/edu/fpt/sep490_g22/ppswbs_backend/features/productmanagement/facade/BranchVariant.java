package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

/**
 * One size at one branch (FR-053, US10): the unit FE-08 reads, guards, holds and settles once stock is per branch.
 * {@code locationId} is the branch of the cart line / order ({@code workshop_locations} id of the canonical list).
 */
public record BranchVariant(long variantId, long locationId) implements Comparable<BranchVariant> {

    public BranchVariant {
        if (variantId <= 0 || locationId <= 0) {
            throw new IllegalArgumentException("variantId and locationId must be positive");
        }
    }

    /** Ascending size, then branch: the lock and guard order of every branch call. */
    @Override
    public int compareTo(BranchVariant other) {
        int bySize = Long.compare(variantId, other.variantId);
        return bySize != 0 ? bySize : Long.compare(locationId, other.locationId);
    }
}
