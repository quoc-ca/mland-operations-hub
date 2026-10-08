package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingMagicLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingMagicLinkRepository extends JpaRepository<BookingMagicLink, Long> {

    Optional<BookingMagicLink> findByBookingIdAndRevokedFalse(Long bookingId);

    Optional<BookingMagicLink> findByTokenHash(String tokenHash);
}
