package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;

/**
 * The locations where a Package is sellable now (003.2 FR-039: Package {@code PUBLISHED}, pair {@code ACTIVE},
 * location active), ascending by id; empty when none or the Package reference is unknown. Names come from the
 * options and offers, not from this read.
 */
public record ActivePackageLocations(List<Long> locationIds) {
    public ActivePackageLocations {
        locationIds = locationIds == null ? List.of() : List.copyOf(locationIds);
    }
}
