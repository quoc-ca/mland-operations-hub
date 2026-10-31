package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkAccess;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MagicLinkAccessRepository extends JpaRepository<MagicLinkAccess, Long> {

    Optional<MagicLinkAccess> findByTokenHash(String tokenHash);

    List<MagicLinkAccess> findByBookingId(Long bookingId);

    List<MagicLinkAccess> findByBookingIdAndLifecycleStatus(Long bookingId, MagicLinkLifecycleStatus status);
}
