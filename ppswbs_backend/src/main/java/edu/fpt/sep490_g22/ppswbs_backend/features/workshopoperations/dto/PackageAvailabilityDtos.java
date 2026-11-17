package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** OpenAPI {@code PackageAvailabilityMatrix}, {@code PackageAvailabilityCell} and {@code PackageAvailabilityWrite}. */
public final class PackageAvailabilityDtos {

    private PackageAvailabilityDtos() {
    }

    public enum State { ACTIVE, INACTIVE }

    public record Cell(Long packageId, Long locationId, State state, Long version) { }

    public record PackageHeader(Long id, String name, String lifecycleState) { }

    public record LocationHeader(Long id, String name, boolean active) { }

    public record Matrix(List<PackageHeader> packages, List<LocationHeader> locations, List<Cell> cells) { }

    /** {@code version} is optional; a stale value returns 409 OPTIMISTIC_LOCK_CONFLICT. */
    public record Write(@NotNull State state, @Min(0) Long version) { }
}
