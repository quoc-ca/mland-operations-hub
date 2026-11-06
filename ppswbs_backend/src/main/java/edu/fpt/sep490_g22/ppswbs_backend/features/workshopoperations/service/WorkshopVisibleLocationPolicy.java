package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Optional local-demo visibility boundary for workshop locations. */
@Component
public class WorkshopVisibleLocationPolicy {

    private final Set<Long> visibleLocationIds;

    public WorkshopVisibleLocationPolicy(
            @Value("${workshop.operations.visible-location-ids:}") String configuredIds) {
        if (configuredIds == null || configuredIds.isBlank()) {
            visibleLocationIds = Set.of();
            return;
        }
        try {
            visibleLocationIds = Arrays.stream(configuredIds.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(Long::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "workshop.operations.visible-location-ids must contain only numeric IDs", ex);
        }
    }

    public boolean allows(Long locationId) {
        return visibleLocationIds.isEmpty() || visibleLocationIds.contains(locationId);
    }
}
