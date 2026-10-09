package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingTicketRepository extends JpaRepository<BookingTicket, Long> {

    Optional<BookingTicket> findByBookingId(Long bookingId);

    Optional<BookingTicket> findByBookingIdAndRevokedFalse(Long bookingId);
}
