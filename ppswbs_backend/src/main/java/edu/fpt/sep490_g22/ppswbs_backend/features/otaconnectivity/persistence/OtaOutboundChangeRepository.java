package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtaOutboundChangeRepository extends JpaRepository<OtaOutboundChange, Long> {
    Optional<OtaOutboundChange> findByMappingIdAndSourceVersionAndChangeKind(
        Long mappingId, Long sourceVersion, OtaChangeKind changeKind);

    Optional<OtaOutboundChange> findTopByMappingIdOrderBySourceVersionDescIdDesc(Long mappingId);

    List<OtaOutboundChange> findAllByMapping_IdAndSourceVersion(Long mappingId, Long sourceVersion);

    List<OtaOutboundChange> findByStatusOrderByCreatedAtAsc(String status);

    boolean existsByMapping_IdAndStatusIn(Long mappingId, List<String> statuses);
}
