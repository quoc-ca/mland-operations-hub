package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtaInboundEventRepository extends JpaRepository<OtaInboundEvent, Long> {
    Optional<OtaInboundEvent> findByConnectionIdAndDedupeKey(Long connectionId, String dedupeKey);
}
