package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.util.List;

/**
 * Provisional cross-feature contract for Workshop Operations 003.2,
 * created per FE-001 owner confirmation; FE-001 owner to review upon branch merge.
 *
 * <p>Provides a privacy-safe, decoupled snapshot of target account capabilities,
 * role, status, and active location assignments without leaking Account entities,
 * repositories, credentials, or PII.
 *
 * @param accountId canonical account ID
 * @param canonicalRole canonical role (MEMBER, STAFF, MANAGER, ADMIN)
 * @param status account status (ACTIVE, PENDING_SETUP, SUSPENDED)
 * @param activeWorkshopLocationIds active workshop location assignments (canonical workshop_location.id)
 */
public record TargetAccountSnapshot(
    Long accountId,
    ActorRole canonicalRole,
    ActorStatus status,
    List<Long> activeWorkshopLocationIds
) {
    public TargetAccountSnapshot {
        activeWorkshopLocationIds = activeWorkshopLocationIds != null ? List.copyOf(activeWorkshopLocationIds) : List.of();
    }

    /**
     * Alias for canonicalRole() for backward/forward compatibility.
     */
    public ActorRole role() {
        return canonicalRole;
    }

    /**
     * Backward-compatibility accessor for consuming features during migration.
     */
    public List<Long> activeLocationIds() {
        return activeWorkshopLocationIds;
    }
}
