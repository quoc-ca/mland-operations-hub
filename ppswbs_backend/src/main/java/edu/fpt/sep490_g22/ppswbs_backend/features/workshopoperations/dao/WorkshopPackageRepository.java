package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface WorkshopPackageRepository extends Repository<WorkshopPackage, Long> {

    Optional<WorkshopPackage> findById(Long packageId);

    WorkshopPackage save(WorkshopPackage workshopPackage);

    List<WorkshopPackage> findByStateOrderByIdAsc(WorkshopPackageState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WorkshopPackage p where p.id = :packageId")
    Optional<WorkshopPackage> findByIdWithLock(@Param("packageId") Long packageId);
}
