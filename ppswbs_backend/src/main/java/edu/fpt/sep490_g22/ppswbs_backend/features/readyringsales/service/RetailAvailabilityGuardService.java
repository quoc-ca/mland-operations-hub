package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
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
 * FE-08 implementation of FE-02's availability boundary. The only native operation is isolated in
 * {@link RetailVariantAvailabilityGuardRepository#provisionIfAbsent(long)} so a first hold has one stable
 * MySQL row to lock. Productmanagement has already locked the corresponding variant before it invokes this
 * boundary; FE-08 must not call productmanagement while this guard is held.
 */
@Service
@RequiredArgsConstructor
public class RetailAvailabilityGuardService implements AvailabilityGuardPort {

    private final RetailVariantAvailabilityGuardRepository guards;
    private final RetailVariantHoldRepository holds;
    private final ThreadLocal<Map<Long, RetailVariantAvailabilityGuard>> activeGuards = new ThreadLocal<>();

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
