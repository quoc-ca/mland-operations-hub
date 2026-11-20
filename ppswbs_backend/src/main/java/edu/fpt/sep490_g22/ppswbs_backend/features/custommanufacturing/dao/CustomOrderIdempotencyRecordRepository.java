package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomOrderIdempotencyRecordRepository extends JpaRepository<CustomOrderIdempotencyRecord, Long> {
    Optional<CustomOrderIdempotencyRecord> findByCommandKeyAndAction(String commandKey, String action);
}
