package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OtaOutboundChangeRepository extends JpaRepository<OtaOutboundChange, Long> {
    /** FR-004a/FR-017: the oldest changes still waiting for delivery, for the outbound delivery worker. */
    List<OtaOutboundChange> findTop50ByStatusInOrderByCreatedAtAscIdAsc(List<String> statuses);

    /** Locks one change for its delivery attempt, so two workers never send it at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OtaOutboundChange c where c.id = :id")
    Optional<OtaOutboundChange> findByIdForUpdate(@Param("id") Long id);

    Optional<OtaOutboundChange> findByMappingIdAndSourceVersionAndChangeKind(
        Long mappingId, Long sourceVersion, OtaChangeKind changeKind);

    Optional<OtaOutboundChange> findTopByMappingIdOrderBySourceVersionDescIdDesc(Long mappingId);

    List<OtaOutboundChange> findAllByMapping_IdAndSourceVersion(Long mappingId, Long sourceVersion);

    List<OtaOutboundChange> findByStatusOrderByCreatedAtAsc(String status);

    boolean existsByMapping_IdAndStatusIn(Long mappingId, List<String> statuses);
}
