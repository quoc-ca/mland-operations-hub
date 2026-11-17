package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

/**
 * Internal seam for the quantity currently held by valid FE-08 holds, per variant. Its runtime
 * implementation is {@link CatalogueAvailabilityService}, which delegates to the FE-08
 * {@code AvailabilityGuardPort}; without that port every read that needs hold data for an ever-published
 * product fails with {@code AVAILABILITY_DEPENDENCY_UNAVAILABLE}. There is no zero-hold fallback.
 */
public interface VariantHoldReader {

    /** Held quantity for every requested variant; a missing or unverifiable value must throw. */
    Map<Long, BigInteger> validHeldByVariant(Collection<Long> variantIds);

    /**
     * FR-051 / FR-053: held quantity per size × branch, for every requested key, once the FE-08 source keeps holds per
     * branch. An empty map means "not known per branch" (the source still holds per size): callers then show the
     * size's holds, never a smaller number. A missing or unverifiable value for a requested key must throw.
     */
    default Map<BranchVariant, BigInteger> validHeldByBranch(Collection<BranchVariant> keys) {
        return Map.of();
    }
}
