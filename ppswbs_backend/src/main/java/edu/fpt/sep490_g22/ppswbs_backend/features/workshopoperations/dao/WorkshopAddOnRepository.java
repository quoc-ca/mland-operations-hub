package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOn;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOnState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkshopAddOnRepository extends JpaRepository<WorkshopAddOn, Long> {
    List<WorkshopAddOn> findByPackageIdAndLocationIdAndStateOrderByDescriptionAsc(
            Long packageId, Long locationId, WorkshopAddOnState state);

    Optional<WorkshopAddOn> findByReferenceAndPackageIdAndLocationId(String reference, Long packageId, Long locationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from WorkshopAddOn a where a.reference = :reference and a.packageId = :packageId and a.locationId = :locationId")
    Optional<WorkshopAddOn> findByReferenceAndScopeWithLock(@Param("reference") String reference,
            @Param("packageId") Long packageId, @Param("locationId") Long locationId);

    Optional<WorkshopAddOn> findByReferenceAndPackageIdAndLocationIdAndState(
            String reference, Long packageId, Long locationId, WorkshopAddOnState state);
}
