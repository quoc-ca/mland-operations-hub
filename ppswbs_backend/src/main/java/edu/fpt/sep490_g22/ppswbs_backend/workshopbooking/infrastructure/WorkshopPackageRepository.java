package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopPackageRepository extends JpaRepository<WorkshopPackage, Long> {

    List<WorkshopPackage> findByStatus(String status);

    Optional<WorkshopPackage> findByIdAndStatus(Long id, String status);
}
