package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementHandoffIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementHandoffIdempotencyRepository extends JpaRepository<SettlementHandoffIdempotency, Long> {
    Optional<SettlementHandoffIdempotency> findByOperationReference(String operationReference);
}
