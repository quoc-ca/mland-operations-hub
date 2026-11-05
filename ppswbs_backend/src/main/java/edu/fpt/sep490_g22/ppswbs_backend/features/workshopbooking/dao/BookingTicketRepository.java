package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingTicketRepository extends JpaRepository<BookingTicket, Long> {

    Optional<BookingTicket> findByTicketCode(String ticketCode);

    Optional<BookingTicket> findByQrReference(String qrReference);

    List<BookingTicket> findByBookingId(Long bookingId);
}
