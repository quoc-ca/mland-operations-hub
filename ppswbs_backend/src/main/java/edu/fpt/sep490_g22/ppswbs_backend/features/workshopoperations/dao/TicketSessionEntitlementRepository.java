package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketSessionEntitlementRepository extends JpaRepository<TicketSessionEntitlement, Long> {

    Optional<TicketSessionEntitlement> findByTicketIdAndSessionId(Long ticketId, Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tse FROM TicketSessionEntitlement tse WHERE tse.ticket.id = :ticketId AND tse.session.id = :sessionId")
    Optional<TicketSessionEntitlement> findByTicketIdAndSessionIdWithLock(@Param("ticketId") Long ticketId, @Param("sessionId") Long sessionId);

    List<TicketSessionEntitlement> findBySessionId(Long sessionId);

    List<TicketSessionEntitlement> findByTicketId(Long ticketId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tse FROM TicketSessionEntitlement tse WHERE tse.ticket.id = :ticketId")
    List<TicketSessionEntitlement> findByTicketIdWithLock(@Param("ticketId") Long ticketId);

    @Query("SELECT COUNT(tse) FROM TicketSessionEntitlement tse WHERE tse.session.id = :sessionId AND tse.ticket.validityState = 'ACTIVE'")
    int countActiveConfirmedSeatsForSession(@Param("sessionId") Long sessionId);

    List<TicketSessionEntitlement> findBySessionIdAndAttendanceState(Long sessionId, AttendanceState state);

    @Query("SELECT tse FROM TicketSessionEntitlement tse WHERE tse.session.id = :sessionId AND tse.ticket.bookingReference = :bookingReference AND tse.ticket.validityState = 'ACTIVE'")
    List<TicketSessionEntitlement> findActiveEntitlementsBySessionIdAndBookingReference(@Param("sessionId") Long sessionId, @Param("bookingReference") String bookingReference);
}
