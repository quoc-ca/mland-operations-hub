package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtaReadinessAssessmentRepository extends JpaRepository<OtaReadinessAssessment, Long> {
    Optional<OtaReadinessAssessment> findTopByMapping_IdOrderByRecordedAtDescIdDesc(Long mappingId);

    List<OtaReadinessAssessment> findAllByMapping_IdOrderByRecordedAtDescIdDesc(Long mappingId);
}
