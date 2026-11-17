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

    // ---- FR-053 (US10, 2026-11-09): holds per size × branch ----
    // The three methods below are additive. Their defaults keep the current per-size source working unchanged:
    // the valid holds of a size are reported for every branch of that size (never fewer than reality, so a branch
    // never looks more available than it is). FE-08 overrides all three once its holds and guard carry the branch.

    /**
     * True once holds, guard and settlement are kept per size × branch. While false, productmanagement still checks
     * quantity changes against the size total and treats the branch snapshots below as an upper bound only.
     */
    default boolean tracksHoldsPerBranch() {
        return false;
    }

    /**
     * Batch read of valid held quantities per size × branch at {@code asOf}; no cache, no lock kept. Exactly one
     * snapshot per requested key ({@link HeldSnapshot#variantId()} is the key's size). Default: the size's holds.
     */
    default Map<BranchVariant, HeldSnapshot> readBranchSnapshots(Collection<BranchVariant> keys, Instant asOf) {
        Map<Long, HeldSnapshot> bySize = readSnapshots(BranchSnapshots.sizesOf(keys), asOf);
        return BranchSnapshots.spread(keys, bySize);
    }

    /**
     * Guard per size × branch (ascending {@link BranchVariant} order), held until the caller's transaction ends, with
     * valid snapshots for {@code work}. FE-08 creates, settles and releases branch holds under the same guard.
     * Default: the size guard of those sizes, each snapshot being the size's holds.
     */
    default <T> T withBranchGuard(Collection<BranchVariant> keys, Instant asOf,
                                  Function<Map<BranchVariant, HeldSnapshot>, T> work) {
        return withGuard(BranchSnapshots.sizesOf(keys), asOf, bySize -> work.apply(BranchSnapshots.spread(keys, bySize)));
    }
}
