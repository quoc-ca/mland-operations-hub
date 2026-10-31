package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtaControlOutboxRepository extends JpaRepository<OtaControlOutboxCommand, Long> {
    Optional<OtaControlOutboxCommand> findByOperationReference(String operationReference);

    Optional<OtaControlOutboxCommand> findByActorAccountReferenceAndIdempotencyKeyHash(
            String actorAccountReference, String idempotencyKeyHash);

    List<OtaControlOutboxCommand> findByStatusOrderByCreatedAtAsc(String status);

    Optional<OtaControlOutboxCommand> findTopByMapping_IdOrderByCreatedAtDesc(Long mappingId);
}
