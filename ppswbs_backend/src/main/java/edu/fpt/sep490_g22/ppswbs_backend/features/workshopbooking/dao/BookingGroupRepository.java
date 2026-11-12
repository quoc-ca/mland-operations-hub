package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingGroupRepository extends JpaRepository<BookingGroup, Long> {
    Optional<BookingGroup> findByGroupCode(String groupCode);

    /** Locks the group so a gateway callback and a sweep cannot confirm or release it at the same time. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select g from BookingGroup g where g.groupCode = :groupCode")
    Optional<BookingGroup> findByGroupCodeForUpdate(
            @org.springframework.data.repository.query.Param("groupCode") String groupCode);

    /** Groups whose 12-minute hold has run out and that nothing has confirmed (FR-032). */
    @org.springframework.data.jpa.repository.Query("""
            select g from BookingGroup g
            where g.groupStatus = edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus.PENDING_PAYMENT
              and g.holdExpiresAt is not null and g.holdExpiresAt <= :now
            order by g.holdExpiresAt asc""")
    java.util.List<BookingGroup> findExpiredPending(
            @org.springframework.data.repository.query.Param("now") java.time.Instant now,
            org.springframework.data.domain.Pageable pageable);
}
