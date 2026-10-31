package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtaDeliveryAttemptRepository extends JpaRepository<OtaDeliveryAttempt, Long> {
    List<OtaDeliveryAttempt> findByOutboundChangeIdOrderByAttemptNumberAsc(Long outboundChangeId);

    java.util.Optional<OtaDeliveryAttempt> findByOutboundChangeIdAndAttemptNumber(Long outboundChangeId, Integer attemptNumber);
}
