package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.PublicCatalogueDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Public list of branches a Member chooses before adding a size to FE-08's cart (decision A, 2026-11-01). The list
 * comes from {@link SellingLocationPort}; a missing or failing source is {@code LOCATION_DEPENDENCY_UNAVAILABLE}, never
 * an empty list, so the product page can disable "Add to cart" and offer a retry while browsing keeps working.
 */
@Service
public class PublicSellingLocationService {

    private final ObjectProvider<SellingLocationPort> port;

    public PublicSellingLocationService(ObjectProvider<SellingLocationPort> port) {
        this.port = port;
    }

    public PublicCatalogueDtos.SellingLocationList list() {
        SellingLocationPort source = port.getIfAvailable();
        if (source == null) {
            throw new CatalogueException(CatalogueErrorCode.LOCATION_DEPENDENCY_UNAVAILABLE);
        }
        List<SellingLocation> locations;
        try {
            locations = source.activeSellingLocations();
        } catch (RuntimeException e) {
            throw new CatalogueException(CatalogueErrorCode.LOCATION_DEPENDENCY_UNAVAILABLE);
        }
        if (locations == null) {
            throw new CatalogueException(CatalogueErrorCode.LOCATION_DEPENDENCY_UNAVAILABLE);
        }
        Set<Long> seen = new HashSet<>();
        for (SellingLocation location : locations) {
            if (location == null || !seen.add(location.id())) {
                throw new CatalogueException(CatalogueErrorCode.LOCATION_DEPENDENCY_UNAVAILABLE);
            }
        }
        return new PublicCatalogueDtos.SellingLocationList(locations.stream()
                .sorted(Comparator.comparing(SellingLocation::displayName).thenComparingLong(SellingLocation::id))
                .map(location -> new PublicCatalogueDtos.PublicSellingLocation(CatalogueValues.format(location.id()),
                        location.displayName(), location.addressSnapshot()))
                .toList());
    }
}
