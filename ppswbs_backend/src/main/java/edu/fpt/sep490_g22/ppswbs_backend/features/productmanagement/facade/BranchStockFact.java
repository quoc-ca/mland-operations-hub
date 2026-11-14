package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Stock of one size at one branch inside a locked branch context (FR-053). {@code remainingUnsold} is the branch
 * quantity before any hold is subtracted; FE-08 subtracts its own valid holds of this size × branch under the guard.
 * For a product not yet assigned to any branch ({@code assignedToBranches} false) it is the size total and
 * {@code saleEnabled} follows the size.
 */
public record BranchStockFact(long variantId, long locationId, BigInteger remainingUnsold, boolean saleEnabled,
                              boolean branchActive, boolean assignedToBranches) {

    public BranchStockFact {
        Objects.requireNonNull(remainingUnsold, "remainingUnsold");
    }

    public BranchVariant key() {
        return new BranchVariant(variantId, locationId);
    }
}
