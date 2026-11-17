package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailBranchAvailabilityGuardRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailBranchAvailabilityGuard;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.HeldSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantAvailabilityGuardRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantAvailabilityGuard;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHoldStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * FE-08 implementation of FE-02's availability boundary. Since T124 (FR-053) holds, guard and settlement are kept
 * per size x branch: {@link #withBranchGuard} locks the size guard rows (ascending size) and then the branch guard
 * rows (ascending size, branch), so callers that still lock by size stay serialized with branch callers.
 * The only native operation is isolated in
 * {@link RetailVariantAvailabilityGuardRepository#provisionIfAbsent(long)} so a first hold has one stable
 * MySQL row to lock. Productmanagement has already locked the corresponding variant before it invokes this
 * boundary; FE-08 must not call productmanagement while this guard is held.
 */
@Service
@RequiredArgsConstructor
public class RetailAvailabilityGuardService implements AvailabilityGuardPort {

    private final RetailVariantAvailabilityGuardRepository guards;
    private final RetailVariantHoldRepository holds;
    private final RetailBranchAvailabilityGuardRepository branchGuards;
    private final ThreadLocal<Map<Long, RetailVariantAvailabilityGuard>> activeGuards = new ThreadLocal<>();
    private final ThreadLocal<Map<BranchVariant, RetailBranchAvailabilityGuard>> activeBranchGuards = new ThreadLocal<>();

    @Override
    public boolean tracksHoldsPerBranch() {
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<BranchVariant, HeldSnapshot> readBranchSnapshots(Collection<BranchVariant> keys, Instant asOf) {
        List<BranchVariant> canonical = canonicalKeys(keys);
        requireAsOf(asOf);
        if (canonical.isEmpty()) {
            return Map.of();
        }
        Map<BranchVariant, Long> revisions = new LinkedHashMap<>();
        branchGuards.findByVariantIdIn(sizesOf(canonical)).forEach(guard -> revisions.put(
            new BranchVariant(guard.getVariantId(), guard.getLocationId()), guard.getVersion() == null ? 0L : guard.getVersion()));
        return branchSnapshots(canonical, asOf, revisions);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T withBranchGuard(Collection<BranchVariant> keys, Instant asOf,
                                 Function<Map<BranchVariant, HeldSnapshot>, T> work) {
        List<BranchVariant> canonical = canonicalKeys(keys);
        requireAsOf(asOf);
        if (work == null) {
            throw unavailable("Availability guard callback is required", sizesOf(canonical));
        }
        if (canonical.isEmpty()) {
            return work.apply(Map.of());
        }
        // Size rows first, then branch rows, both in ascending order: one global lock order for every caller.
        return withGuard(sizesOf(canonical), asOf, ignoredSizeSnapshots -> {
            for (BranchVariant key : canonical) {
                branchGuards.provisionIfAbsent(key.variantId(), key.locationId());
            }
            branchGuards.flush();
            Map<BranchVariant, RetailBranchAvailabilityGuard> locked = new LinkedHashMap<>();
            Map<BranchVariant, Long> revisions = new LinkedHashMap<>();
            for (BranchVariant key : canonical) {
                RetailBranchAvailabilityGuard guard = branchGuards.lockOne(key.variantId(), key.locationId())
                    .orElseThrow(() -> unavailable("Branch availability guard could not be locked", List.of(key.variantId())));
                locked.put(key, guard);
                revisions.put(key, guard.getVersion() == null ? 0L : guard.getVersion());
            }
            Map<BranchVariant, HeldSnapshot> snapshots = branchSnapshots(canonical, asOf, revisions);
            activeBranchGuards.set(Map.copyOf(locked));
            try {
                return work.apply(snapshots);
            } finally {
                activeBranchGuards.remove();
            }
        });
    }

    /**
     * Internal FE-08 guard check for a branch hold mutation: the branch guard must be held by this thread (or, for
     * legacy size callers, the size guard, which serializes every branch of that size). Both rows are touched.
     */
    void touchLockedBranchGuard(BranchVariant key) {
        Map<BranchVariant, RetailBranchAvailabilityGuard> branches = activeBranchGuards.get();
        RetailBranchAvailabilityGuard branchGuard = branches == null || key == null ? null : branches.get(key);
        Map<Long, RetailVariantAvailabilityGuard> sizes = activeGuards.get();
        RetailVariantAvailabilityGuard sizeGuard = sizes == null || key == null ? null : sizes.get(key.variantId());
        if (branchGuard == null && sizeGuard == null) {
            throw unavailable("Branch hold mutation is outside its source guard", key == null ? List.of() : List.of(key.variantId()));
        }
        if (branchGuard != null) branchGuard.touch();
        if (sizeGuard != null) sizeGuard.touch();
    }

    /** Branch hold totals plus legacy size-level holds, which count at every branch of their size. */
    private Map<BranchVariant, HeldSnapshot> branchSnapshots(List<BranchVariant> keys, Instant asOf,
                                                             Map<BranchVariant, Long> revisions) {
        Map<BranchVariant, Long> held = new LinkedHashMap<>();
        Map<Long, Long> unattributed = new LinkedHashMap<>();
        holds.sumValidHeldByVariantAndLocation(sizesOf(keys), RetailVariantHoldStatus.HELD, asOf).forEach(value -> {
            if (value.getLocationId() == null || value.getLocationId() == RetailVariantHold.UNATTRIBUTED_LOCATION) {
                unattributed.merge(value.getVariantId(), value.getHeldQuantity(), Long::sum);
            } else {
                held.merge(new BranchVariant(value.getVariantId(), value.getLocationId()), value.getHeldQuantity(), Long::sum);
            }
        });
        Map<BranchVariant, HeldSnapshot> snapshots = new LinkedHashMap<>();
        for (BranchVariant key : keys) {
            long quantity = held.getOrDefault(key, 0L) + unattributed.getOrDefault(key.variantId(), 0L);
            snapshots.put(key, new HeldSnapshot(key.variantId(), BigInteger.valueOf(quantity), revisions.getOrDefault(key, 0L)));
        }
        return Map.copyOf(snapshots);
    }

    private List<BranchVariant> canonicalKeys(Collection<BranchVariant> keys) {
        if (keys == null) {
            throw unavailable("Branch keys are required", List.of());
        }
        TreeSet<BranchVariant> values = new TreeSet<>();
        for (BranchVariant key : keys) {
            if (key == null || key.variantId() <= 0 || key.locationId() <= 0) {
                throw unavailable("Branch keys must have a positive size and branch", List.of());
            }
            values.add(key);
        }
        return List.copyOf(values);
    }

    private static List<Long> sizesOf(List<BranchVariant> keys) {
        return keys.stream().map(BranchVariant::variantId).distinct().sorted().toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<Long, HeldSnapshot> readSnapshots(Collection<Long> variantIds, Instant asOf) {
        List<Long> ids = canonicalIds(variantIds);
        requireAsOf(asOf);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> quantities = heldQuantities(ids, asOf);
        Map<Long, RetailVariantAvailabilityGuard> existing = new LinkedHashMap<>();
        guards.findAllById(ids).forEach(guard -> existing.put(guard.getVariantId(), guard));
        Map<Long, HeldSnapshot> snapshots = new LinkedHashMap<>();
        for (Long id : ids) {
            long revision = existing.containsKey(id) && existing.get(id).getVersion() != null
                ? existing.get(id).getVersion() : 0L;
            snapshots.put(id, new HeldSnapshot(id, BigInteger.valueOf(quantities.getOrDefault(id, 0L)), revision));
        }
        return Map.copyOf(snapshots);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T withGuard(Collection<Long> variantIds, Instant asOf, Function<Map<Long, HeldSnapshot>, T> work) {
        List<Long> ids = canonicalIds(variantIds);
        requireAsOf(asOf);
        if (work == null) {
            throw unavailable("Availability guard callback is required", ids);
        }
        if (ids.isEmpty()) {
            return work.apply(Map.of());
        }
        for (Long id : ids) {
            guards.provisionIfAbsent(id);
        }
        guards.flush();
        List<RetailVariantAvailabilityGuard> locked = guards.findByVariantIdInOrderByVariantIdAsc(ids);
        if (locked.size() != ids.size()) {
            throw unavailable("Availability guard could not be locked", ids);
        }
        Map<Long, RetailVariantAvailabilityGuard> byId = new LinkedHashMap<>();
        locked.forEach(guard -> byId.put(guard.getVariantId(), guard));
        if (!byId.keySet().containsAll(ids)) {
            throw unavailable("Availability guard returned incomplete rows", ids);
        }
        Map<Long, Long> quantities = heldQuantities(ids, asOf);
        Map<Long, HeldSnapshot> snapshots = new LinkedHashMap<>();
        for (Long id : ids) {
            RetailVariantAvailabilityGuard guard = byId.get(id);
            long revision = guard.getVersion() == null ? 0L : guard.getVersion();
            snapshots.put(id, new HeldSnapshot(id, BigInteger.valueOf(quantities.getOrDefault(id, 0L)), revision));
        }
        activeGuards.set(Map.copyOf(byId));
        try {
            return work.apply(Map.copyOf(snapshots));
        } finally {
            activeGuards.remove();
        }
    }

    /** Internal FE-08 guard check for hold mutations; only valid in the active withGuard callback. */
    void touchLockedGuard(Long variantId) {
        Map<Long, RetailVariantAvailabilityGuard> locked = activeGuards.get();
        RetailVariantAvailabilityGuard guard = locked == null ? null : locked.get(variantId);
        if (guard == null) {
            throw unavailable("Variant hold mutation is outside its source guard", variantId == null ? List.of() : List.of(variantId));
        }
        guard.touch();
    }

    private Map<Long, Long> heldQuantities(List<Long> variantIds, Instant asOf) {
        Map<Long, Long> quantities = new LinkedHashMap<>();
        holds.sumValidHeldByVariantIds(variantIds, RetailVariantHoldStatus.HELD, asOf)
            .forEach(value -> quantities.put(value.getVariantId(), value.getHeldQuantity()));
        return quantities;
    }

    private List<Long> canonicalIds(Collection<Long> variantIds) {
        if (variantIds == null) {
            throw unavailable("Variant IDs are required", List.of());
        }
        TreeSet<Long> values = new TreeSet<>();
        for (Long variantId : variantIds) {
            if (variantId == null || variantId <= 0) {
                throw unavailable("Variant IDs must be positive", List.of());
            }
            values.add(variantId);
        }
        return List.copyOf(values);
    }

    private void requireAsOf(Instant asOf) {
        if (asOf == null) {
            throw unavailable("Availability snapshot time is required", List.of());
        }
    }

    private CatalogueSalesException unavailable(String message, List<Long> variantIds) {
        return new CatalogueSalesException(CatalogueSalesException.Reason.DEPENDENCY_UNAVAILABLE, message, variantIds);
    }
}
