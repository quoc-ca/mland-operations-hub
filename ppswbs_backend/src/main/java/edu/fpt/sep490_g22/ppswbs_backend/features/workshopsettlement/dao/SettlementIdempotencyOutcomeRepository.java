package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementIdempotencyOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SettlementIdempotencyOutcomeRepository extends JpaRepository<SettlementIdempotencyOutcome, Long> { Optional<SettlementIdempotencyOutcome> findByOperationReference(String operationReference); }
