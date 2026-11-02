package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.HeldSnapshot;
import org.springframework.beans.factory.ObjectProvider;
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
 * Binding of {@link AvailabilityGuardPort} inside productmanagement (T019). It is the runtime
 * {@link VariantHoldReader} and the only place that calls the FE-08 source.
 *
 * <ul>
 *   <li>Without an implementation bean every call fails with {@code AVAILABILITY_DEPENDENCY_UNAVAILABLE};
 *       there is no zero-hold fallback.</li>
 *   <li>A result missing a requested size, or carrying a snapshot for another size, is unverifiable and fails
 *       the same way. A {@link CatalogueSalesException} {@code DEPENDENCY_UNAVAILABLE} from the source is
 *       translated; other exceptions propagate and roll the transaction back.</li>
 *   <li>The guard is requested in ascending variant ID order, after the catalogue row locks (lock order step 5).</li>
 * </ul>
 */
@Service
public class CatalogueAvailabilityService implements VariantHoldReader {

    private final ObjectProvider<AvailabilityGuardPort> port;

    public CatalogueAvailabilityService(ObjectProvider<AvailabilityGuardPort> port) {
        this.port = port;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, BigInteger> validHeldByVariant(Collection<Long> variantIds) {
        Map<Long, BigInteger> held = new LinkedHashMap<>();
        readSnapshots(variantIds, Instant.now()).forEach((id, snapshot) -> held.put(id, snapshot.heldQuantity()));
        return held;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, HeldSnapshot> readSnapshots(Collection<Long> variantIds, Instant asOf) {
        List<Long> ids = ascending(variantIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return verified(ids, requirePort().readSnapshots(ids, asOf));
        } catch (CatalogueSalesException e) {
            throw translate(e);
        }
    }

    /** Runs {@code work} under the FE-08 source guard of the given sizes, held until commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T withGuard(Collection<Long> variantIds, Instant asOf, Function<Map<Long, HeldSnapshot>, T> work) {
        List<Long> ids = ascending(variantIds);
        try {
            return requirePort().withGuard(ids, asOf, snapshots -> work.apply(verified(ids, snapshots)));
        } catch (CatalogueSalesException e) {
            throw translate(e);
        }
    }

    private AvailabilityGuardPort requirePort() {
        AvailabilityGuardPort source = port.getIfAvailable();
        if (source == null) {
            throw new CatalogueException(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
        }
        return source;
    }

    private static Map<Long, HeldSnapshot> verified(List<Long> ids, Map<Long, HeldSnapshot> snapshots) {
        if (snapshots == null || snapshots.size() != ids.size()) {
            throw new CatalogueException(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
        }
        Map<Long, HeldSnapshot> result = new LinkedHashMap<>();
        for (Long id : ids) {
            HeldSnapshot snapshot = snapshots.get(id);
            if (snapshot == null || snapshot.variantId() != id) {
                throw new CatalogueException(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
            }
            result.put(id, snapshot);
        }
        return Map.copyOf(result);
    }

    private static RuntimeException translate(CatalogueSalesException e) {
        return e.reason() == CatalogueSalesException.Reason.DEPENDENCY_UNAVAILABLE
                ? new CatalogueException(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE)
                : e;
    }

    private static List<Long> ascending(Collection<Long> variantIds) {
        return List.copyOf(new TreeSet<>(variantIds));
    }
}
