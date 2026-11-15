package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OtaControlOutboxRepository extends JpaRepository<OtaControlOutboxCommand, Long> {
    Optional<OtaControlOutboxCommand> findByOperationReference(String operationReference);

    Optional<OtaControlOutboxCommand> findByActorAccountReferenceAndIdempotencyKeyHash(
            String actorAccountReference, String idempotencyKeyHash);

    List<OtaControlOutboxCommand> findByStatusOrderByCreatedAtAsc(String status);

    /** 003.4 T120: the oldest control commands still waiting for provider delivery. */
    List<OtaControlOutboxCommand> findTop50ByStatusOrderByCreatedAtAscIdAsc(String status);

    /** Locks one command for its delivery, so two workers never send it at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OtaControlOutboxCommand c where c.id = :id")
    Optional<OtaControlOutboxCommand> findByIdForUpdate(@Param("id") Long id);

    Optional<OtaControlOutboxCommand> findTopByMapping_IdOrderByCreatedAtDesc(Long mappingId);
}
