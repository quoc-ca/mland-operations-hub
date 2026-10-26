package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopBranchRepository extends JpaRepository<WorkshopBranch, Long> {

    Optional<WorkshopBranch> findByReference(String reference);

    Optional<WorkshopBranch> findByCode(String code);

    List<WorkshopBranch> findByActiveTrue();

    List<WorkshopBranch> findByIdInAndActiveTrue(List<Long> ids);
}
