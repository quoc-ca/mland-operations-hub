package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OtaChannelBookingRepository extends JpaRepository<OtaChannelBooking, Long> {
    Optional<OtaChannelBooking> findByPublicReference(String publicReference);

    Optional<OtaChannelBooking> findByMappingIdAndExternalBookingReference(
            Long mappingId, String externalBookingReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from OtaChannelBooking booking where booking.publicReference = :reference")
    Optional<OtaChannelBooking> findByPublicReferenceForUpdate(@Param("reference") String publicReference);
}
