package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/**
 * SPI owned by productmanagement and implemented by the FE-08 hold source (contracts/interfaces.md,
 * "AvailabilityGuardPort", T019). productmanagement never imports readyringsales; without an implementation
 * bean every read that needs held quantities fails with {@code AVAILABILITY_DEPENDENCY_UNAVAILABLE}.
 *
 * <ul>
 *   <li>Both methods join the caller's transaction (MANDATORY); they never open REQUIRES_NEW.</li>
 *   <li>The result has exactly one snapshot per requested ID. Missing or unverifiable data must throw
 *       (preferably {@link CatalogueSalesException} with {@code DEPENDENCY_UNAVAILABLE}), never return zero.</li>
 *   <li>The implementation must not call back into productmanagement while it holds the source guard.</li>
 * </ul>
 */
public interface AvailabilityGuardPort {

    /** Batch read of exact held quantities at {@code asOf}; no cache, no lock kept. */
    Map<Long, HeldSnapshot> readSnapshots(Collection<Long> variantIds, Instant asOf);

    /**
     * Takes a stable guard row per size in ascending variant ID order (also when the size has no hold yet),
     * reads valid snapshots and runs {@code work} while the guard is held; the guard stays held until the
     * caller's transaction ends. FE-08 creates, settles and releases holds under the same guard.
     */
    <T> T withGuard(Collection<Long> variantIds, Instant asOf, Function<Map<Long, HeldSnapshot>, T> work);
}
