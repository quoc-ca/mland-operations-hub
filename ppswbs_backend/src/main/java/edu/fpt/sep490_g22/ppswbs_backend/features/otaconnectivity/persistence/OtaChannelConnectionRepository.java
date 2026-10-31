package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtaChannelConnectionRepository extends JpaRepository<OtaChannelConnection, Long> {
    Optional<OtaChannelConnection> findByProviderCodeAndEnvironment(OtaProvider providerCode, OtaEnvironment environment);
    Optional<OtaChannelConnection> findByPublicReference(String publicReference);
}
