package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopLocationRepository extends JpaRepository<WorkshopLocation, Long> {

    Optional<WorkshopLocation> findByCode(String code);

    List<WorkshopLocation> findByActiveTrue();

    List<WorkshopLocation> findByIdInAndActiveTrue(List<Long> ids);
}
