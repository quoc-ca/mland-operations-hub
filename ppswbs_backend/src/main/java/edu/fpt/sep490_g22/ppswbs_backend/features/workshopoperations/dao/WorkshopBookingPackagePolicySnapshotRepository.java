package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBookingPackagePolicySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkshopBookingPackagePolicySnapshotRepository
        extends JpaRepository<WorkshopBookingPackagePolicySnapshot, Long> {
    Optional<WorkshopBookingPackagePolicySnapshot> findByBookingReference(String bookingReference);
}
