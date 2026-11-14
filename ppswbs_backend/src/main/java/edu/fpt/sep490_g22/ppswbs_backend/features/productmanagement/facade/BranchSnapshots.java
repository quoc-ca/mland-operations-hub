package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Helpers of the {@link AvailabilityGuardPort} branch defaults: size snapshots reported for each branch key. */
final class BranchSnapshots {

    private BranchSnapshots() {
    }

    /** Distinct sizes of the keys, ascending. */
    static List<Long> sizesOf(Collection<BranchVariant> keys) {
        TreeSet<Long> sizes = new TreeSet<>();
        keys.forEach(key -> sizes.add(key.variantId()));
        return List.copyOf(sizes);
    }

    /** One snapshot per key (ascending), each the snapshot of its size; a missing size stays missing. */
    static Map<BranchVariant, HeldSnapshot> spread(Collection<BranchVariant> keys, Map<Long, HeldSnapshot> bySize) {
        Map<BranchVariant, HeldSnapshot> result = new LinkedHashMap<>();
        if (bySize == null) {
            return result;
        }
        new TreeSet<>(keys).forEach(key -> {
            HeldSnapshot snapshot = bySize.get(key.variantId());
            if (snapshot != null) {
                result.put(key, snapshot);
            }
        });
        return result;
    }
}
