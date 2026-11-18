package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsultationIdempotencyRecordRepository extends JpaRepository<ConsultationIdempotencyRecord, String> {

    Optional<ConsultationIdempotencyRecord> findByIdempotencyKeyAndActorScope(String idempotencyKey, String actorScope);
}
