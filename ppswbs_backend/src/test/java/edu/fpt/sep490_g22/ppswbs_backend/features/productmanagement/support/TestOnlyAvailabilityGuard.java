package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.HeldSnapshot;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * TEST-ONLY stand-in for the FE-08 hold source behind {@link AvailabilityGuardPort}. Held quantities live in
 * memory (default zero), there is no real guard row, and it is never part of the runtime: tests using it
 * prove productmanagement's side only. Registered as {@code @Primary} over the real FE-08 source; the real
 * source is exercised by CatalogueQuantityIT.
 */
public class TestOnlyAvailabilityGuard implements AvailabilityGuardPort {

    private final Map<Long, BigInteger> held = new ConcurrentHashMap<>();
    private final List<List<Long>> guardCalls = new ArrayList<>();
    private volatile boolean unavailable;

    public void setHeld(long variantId, long quantity) {
        held.put(variantId, BigInteger.valueOf(quantity));
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public void reset() {
        held.clear();
        unavailable = false;
        synchronized (guardCalls) {
            guardCalls.clear();
        }
    }

    /** Variant ID lists passed to {@link #withGuard}, in call order. */
    public List<List<Long>> guardCalls() {
        synchronized (guardCalls) {
            return List.copyOf(guardCalls);
        }
    }

    @Override
    public Map<Long, HeldSnapshot> readSnapshots(Collection<Long> variantIds, Instant asOf) {
        if (unavailable) {
            throw new CatalogueSalesException(CatalogueSalesException.Reason.DEPENDENCY_UNAVAILABLE, "test source down");
        }
        Map<Long, HeldSnapshot> result = new LinkedHashMap<>();
        for (Long id : variantIds) {
            result.put(id, new HeldSnapshot(id, held.getOrDefault(id, BigInteger.ZERO), 0));
        }
        return result;
    }

    @Override
    public <T> T withGuard(Collection<Long> variantIds, Instant asOf, Function<Map<Long, HeldSnapshot>, T> work) {
        synchronized (guardCalls) {
            guardCalls.add(List.copyOf(variantIds));
        }
        return work.apply(readSnapshots(variantIds, asOf));
    }
}
