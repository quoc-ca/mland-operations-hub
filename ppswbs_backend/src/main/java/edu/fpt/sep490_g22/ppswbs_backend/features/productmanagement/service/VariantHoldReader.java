package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

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
}
