package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtaChannelMappingRepository extends JpaRepository<OtaChannelMapping, Long> {
    Optional<OtaChannelMapping> findByConnectionIdAndSessionReference(Long connectionId, String sessionReference);

    List<OtaChannelMapping> findByConnectionIdAndStatusOrderByIdAsc(Long connectionId, OtaMappingStatus status);
}
