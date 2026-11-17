package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageLocationAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopPackageLocationAvailabilityRepository
        extends JpaRepository<WorkshopPackageLocationAvailability, Long> {

    Optional<WorkshopPackageLocationAvailability> findByPackageIdAndLocationId(Long packageId, Long locationId);

    List<WorkshopPackageLocationAvailability> findByPackageId(Long packageId);
}
