package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface OtaPayoutPositionRepository extends JpaRepository<OtaPayoutPosition, Long> {
    List<OtaPayoutPosition> findAllByChannelBookingIdOrderByCreatedAtAscIdAsc(Long channelBookingId);

    Optional<OtaPayoutPosition> findByIdAndChannelBookingId(Long id, Long channelBookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select position from OtaPayoutPosition position where position.channelBookingId = :bookingId order by position.id")
    List<OtaPayoutPosition> findByChannelBookingIdForUpdate(@Param("bookingId") Long bookingId);
}
