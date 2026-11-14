package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupMagicLink;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkLifecycleStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingGroupMagicLinkRepository extends JpaRepository<BookingGroupMagicLink, Long> {

    Optional<BookingGroupMagicLink> findByTokenHash(String tokenHash);

    List<BookingGroupMagicLink> findByGroupIdAndLifecycleStatus(Long groupId, MagicLinkLifecycleStatus lifecycleStatus);

    /** Active links whose lifetime has run out, so the sweep can expire them and scrub their URL (FR-017b9a). */
    @Query("select l from BookingGroupMagicLink l where l.lifecycleStatus = "
            + "edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkLifecycleStatus.ACTIVE "
            + "and l.expiresAt <= :now order by l.expiresAt asc")
    List<BookingGroupMagicLink> findActiveExpired(@Param("now") Instant now, Pageable pageable);
}
