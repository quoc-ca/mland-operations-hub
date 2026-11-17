package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCancellationCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementCancellationCaseRepository extends JpaRepository<SettlementCancellationCase, Long> {
    Optional<SettlementCancellationCase> findByCaseReference(String caseReference);
    Optional<SettlementCancellationCase> findByOperationReference(String operationReference);
    java.util.List<SettlementCancellationCase> findBySessionReferenceIgnoreCaseAndStatus(String sessionReference,
            edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCaseStatus status);
    java.util.List<SettlementCancellationCase> findByStatus(
            edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCaseStatus status);
}
