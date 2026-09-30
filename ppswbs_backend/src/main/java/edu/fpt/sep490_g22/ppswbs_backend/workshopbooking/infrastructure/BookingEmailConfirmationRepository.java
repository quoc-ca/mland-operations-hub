package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingEmailConfirmation;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.ConfirmationTokenState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingEmailConfirmationRepository extends JpaRepository<BookingEmailConfirmation, Long> {
    Optional<BookingEmailConfirmation> findByBookingId(Long bookingId);
    List<BookingEmailConfirmation> findByStateAndExpiresAtBefore(ConfirmationTokenState state, Instant now);
}
