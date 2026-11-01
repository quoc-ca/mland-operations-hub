package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantAvailabilityGuard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RetailVariantAvailabilityGuardRepository extends JpaRepository<RetailVariantAvailabilityGuard, Long> {

    /** The sole approved native query: atomically provisions the stable guard row for one variant. */
    @Modifying
    @Query(value = "INSERT INTO retail_variant_availability_guards (variant_id, version) VALUES (:variantId, 0) "
        + "ON DUPLICATE KEY UPDATE variant_id = VALUES(variant_id)", nativeQuery = true)
    void provisionIfAbsent(@Param("variantId") long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RetailVariantAvailabilityGuard> findByVariantIdInOrderByVariantIdAsc(List<Long> variantIds);
}
