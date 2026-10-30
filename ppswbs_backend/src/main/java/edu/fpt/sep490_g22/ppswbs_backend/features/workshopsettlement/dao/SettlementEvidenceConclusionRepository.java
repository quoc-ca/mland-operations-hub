package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SettlementEvidenceConclusionRepository extends JpaRepository<SettlementEvidenceConclusion, Long> { Optional<SettlementEvidenceConclusion> findByOperationReference(String operationReference); }
