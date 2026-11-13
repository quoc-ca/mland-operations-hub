package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketBookingRepository extends JpaRepository<TicketBooking, Long> {
    Optional<TicketBooking> findByTicketCode(String ticketCode);

    /** The ticket holding a 003.2 ticket reference (003.1 T099). */
    Optional<TicketBooking> findByOperationsTicketReference(String operationsTicketReference);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from TicketBooking t where t.ticketCode = :ticketCode")
    Optional<TicketBooking> findByTicketCodeForUpdate(
            @org.springframework.data.repository.query.Param("ticketCode") String ticketCode);

    List<TicketBooking> findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(Long groupId);
}
