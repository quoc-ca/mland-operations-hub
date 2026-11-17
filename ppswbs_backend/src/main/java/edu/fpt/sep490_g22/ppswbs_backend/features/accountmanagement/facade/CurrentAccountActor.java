package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.util.List;

public record CurrentAccountActor(
    Long accountId,
    ActorRole role,
    ActorStatus status,
    boolean emailVerified,
    List<Long> activeWorkshopLocationIds
) {
    public CurrentAccountActor {
        activeWorkshopLocationIds = activeWorkshopLocationIds != null ? List.copyOf(activeWorkshopLocationIds) : List.of();
    }

    /**
     * Backward-compatibility accessor for consuming features during migration.
     */
    public List<Long> activeLocationIds() {
        return activeWorkshopLocationIds;
    }
}
