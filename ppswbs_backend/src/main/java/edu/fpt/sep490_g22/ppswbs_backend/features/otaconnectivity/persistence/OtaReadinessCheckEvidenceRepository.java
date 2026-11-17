package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtaReadinessCheckEvidenceRepository extends JpaRepository<OtaReadinessCheckEvidence, Long> {
    List<OtaReadinessCheckEvidence> findAllByAssessment_IdOrderByCheckCodeAsc(Long assessmentId);
}
