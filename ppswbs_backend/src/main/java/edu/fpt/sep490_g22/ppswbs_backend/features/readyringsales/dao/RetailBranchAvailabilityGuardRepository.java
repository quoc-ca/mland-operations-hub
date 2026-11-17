package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailBranchAvailabilityGuard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RetailBranchAvailabilityGuardRepository
    extends JpaRepository<RetailBranchAvailabilityGuard, RetailBranchAvailabilityGuard.Key> {

    /** Atomically provisions the stable guard row for one size at one branch. */
    @Modifying
    @Query(value = "INSERT INTO retail_branch_availability_guards (variant_id, location_id, version) "
        + "VALUES (:variantId, :locationId, 0) ON DUPLICATE KEY UPDATE variant_id = VALUES(variant_id)", nativeQuery = true)
    void provisionIfAbsent(@Param("variantId") long variantId, @Param("locationId") long locationId);

    /** Locks one guard row; callers lock keys one by one in ascending (variant, branch) order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guard from RetailBranchAvailabilityGuard guard where guard.variantId = :variantId and guard.locationId = :locationId")
    Optional<RetailBranchAvailabilityGuard> lockOne(@Param("variantId") Long variantId, @Param("locationId") Long locationId);

    List<RetailBranchAvailabilityGuard> findByVariantIdIn(List<Long> variantIds);
}
