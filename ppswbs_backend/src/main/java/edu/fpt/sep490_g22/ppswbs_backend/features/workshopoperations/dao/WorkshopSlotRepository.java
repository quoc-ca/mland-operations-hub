package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopSlotRepository extends JpaRepository<WorkshopSlot, Long> {

    Optional<WorkshopSlot> findByReference(String reference);

    List<WorkshopSlot> findByBranchId(Long branchId);

    List<WorkshopSlot> findByBranchIdAndActiveTrue(Long branchId);
}
