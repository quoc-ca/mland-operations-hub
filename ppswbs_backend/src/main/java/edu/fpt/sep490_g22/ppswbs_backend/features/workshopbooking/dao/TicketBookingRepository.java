package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketBookingRepository extends JpaRepository<TicketBooking, Long> {
    Optional<TicketBooking> findByTicketCode(String ticketCode);

    List<TicketBooking> findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(Long groupId);
}
