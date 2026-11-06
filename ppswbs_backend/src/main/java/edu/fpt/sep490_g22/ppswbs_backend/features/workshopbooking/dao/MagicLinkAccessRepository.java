package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkAccess;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MagicLinkAccessRepository extends JpaRepository<MagicLinkAccess, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select link from MagicLinkAccess link where link.tokenHash = :tokenHash")
    Optional<MagicLinkAccess> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<MagicLinkAccess> findByBookingId(Long bookingId);

    List<MagicLinkAccess> findByBookingIdAndLifecycleStatus(Long bookingId, MagicLinkLifecycleStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MagicLinkAccess> findByBookingIdAndLifecycleStatusOrderByIdAsc(Long bookingId, MagicLinkLifecycleStatus status);

    @Query("select link from MagicLinkAccess link where (link.lifecycleStatus in :statuses or link.expiresAt <= :now) and link.deliveryValueScrubbedAt is null")
    List<MagicLinkAccess> findLinksWhoseSnapshotsMustBeScrubbed(
            @Param("statuses") List<MagicLinkLifecycleStatus> statuses,
            @Param("now") Instant now);
}
