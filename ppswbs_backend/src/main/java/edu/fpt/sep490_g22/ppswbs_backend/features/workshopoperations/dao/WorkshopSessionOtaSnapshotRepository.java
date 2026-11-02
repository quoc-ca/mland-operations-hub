package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSessionOtaSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkshopSessionOtaSnapshotRepository extends JpaRepository<WorkshopSessionOtaSnapshot, Long> {
    Optional<WorkshopSessionOtaSnapshot> findTopBySessionReferenceOrderBySourceVersionDesc(String sessionReference);
}
