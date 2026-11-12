package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupMagicLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingGroupMagicLinkRepository extends JpaRepository<BookingGroupMagicLink, Long> {

    Optional<BookingGroupMagicLink> findByTokenHash(String tokenHash);
}
