package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.CapacityHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CapacityHoldRepository extends JpaRepository<CapacityHold, Long> {

    Optional<CapacityHold> findByBookingId(Long bookingId);

    /** Find all active (unreleased, non-expired) holds for a session. */
    @Query("SELECT h FROM CapacityHold h WHERE h.sessionId = :sessionId AND h.releaseGeneration = 0 AND h.expiresAt > :now")
    List<CapacityHold> findActiveHoldsBySessionId(@Param("sessionId") Long sessionId, @Param("now") Instant now);

    /** Find all expired but unreleased holds. */
    @Query("SELECT h FROM CapacityHold h WHERE h.releaseGeneration = 0 AND h.expiresAt <= :now")
    List<CapacityHold> findExpiredUnreleasedHolds(@Param("now") Instant now);
}
