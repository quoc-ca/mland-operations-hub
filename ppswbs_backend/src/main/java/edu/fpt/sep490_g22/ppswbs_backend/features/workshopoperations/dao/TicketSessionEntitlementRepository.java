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

    @Query("SELECT tse FROM TicketSessionEntitlement tse WHERE tse.ticket.bookingReference = :bookingReference AND tse.ticket.validityState = 'ACTIVE'")
    List<TicketSessionEntitlement> findActiveByBookingReference(@Param("bookingReference") String bookingReference);

    /** The valid tickets of one session, grouped by booking, for the attendance roster. */
    @Query("SELECT tse FROM TicketSessionEntitlement tse WHERE tse.session.id = :sessionId AND tse.ticket.validityState = ACTIVE "
            + "ORDER BY tse.ticket.bookingReference, tse.ticket.ticketSequence, tse.id")
    List<TicketSessionEntitlement> findActiveBySessionIdOrdered(@Param("sessionId") Long sessionId);

    /** Bookings with active tickets whose attendance-finalization publication is not yet recorded. */
    @Query("SELECT DISTINCT tse.ticket.bookingReference FROM TicketSessionEntitlement tse "
            + "WHERE tse.ticket.validityState = 'ACTIVE' AND NOT EXISTS ("
            + "SELECT 1 FROM BookingAttendanceFinalizationOutboxEvent o "
            + "WHERE o.bookingReference = tse.ticket.bookingReference)")
    List<String> findDistinctActiveBookingReferences();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tse FROM TicketSessionEntitlement tse WHERE tse.ticket.bookingReference = :bookingReference AND tse.ticket.validityState = 'ACTIVE' ORDER BY tse.id")
    List<TicketSessionEntitlement> findActiveByBookingReferenceWithLock(@Param("bookingReference") String bookingReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tse FROM TicketSessionEntitlement tse WHERE tse.ticket.id = :ticketId")
    List<TicketSessionEntitlement> findByTicketIdWithLock(@Param("ticketId") Long ticketId);

    @Query("SELECT COUNT(tse) FROM TicketSessionEntitlement tse WHERE tse.session.id = :sessionId AND tse.ticket.validityState = 'ACTIVE'")
    int countActiveConfirmedSeatsForSession(@Param("sessionId") Long sessionId);

    List<TicketSessionEntitlement> findBySessionIdAndAttendanceState(Long sessionId, AttendanceState state);

    @Query("SELECT tse FROM TicketSessionEntitlement tse WHERE tse.session.id = :sessionId AND tse.ticket.bookingReference = :bookingReference AND tse.ticket.validityState = 'ACTIVE'")
    List<TicketSessionEntitlement> findActiveEntitlementsBySessionIdAndBookingReference(@Param("sessionId") Long sessionId, @Param("bookingReference") String bookingReference);
}
