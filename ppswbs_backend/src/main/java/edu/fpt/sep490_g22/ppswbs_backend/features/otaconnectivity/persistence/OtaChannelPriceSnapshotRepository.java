package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface OtaChannelPriceSnapshotRepository extends JpaRepository<OtaChannelPriceSnapshot, Long> {

    List<OtaChannelPriceSnapshot> findAllByMapping_IdOrderByEffectiveFromDesc(Long mappingId);

    Optional<OtaChannelPriceSnapshot> findTopByMapping_IdOrderByEffectiveFromDesc(Long mappingId);

    Optional<OtaChannelPriceSnapshot> findByPublicReference(String publicReference);

    Optional<OtaChannelPriceSnapshot> findByMapping_IdAndEffectiveFrom(Long mappingId, Instant effectiveFrom);

    Optional<OtaChannelPriceSnapshot> findFirstByMapping_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long mappingId, Instant effectiveFrom);

    boolean existsByMapping_Id(Long mappingId);

    default OtaCommercialStatus resolveCommercialStatus(Long mappingId) {
        return OtaCommercialStatus.fromConfirmedSnapshot(existsByMapping_Id(mappingId));
    }
}
