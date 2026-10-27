package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketSessionEntitlementRepository extends JpaRepository<TicketSessionEntitlement, Long> {

    Optional<TicketSessionEntitlement> findByTicketIdAndSessionId(Long ticketId, Long sessionId);

    List<TicketSessionEntitlement> findBySessionId(Long sessionId);

    List<TicketSessionEntitlement> findByTicketId(Long ticketId);

    @Query("SELECT COUNT(tse) FROM TicketSessionEntitlement tse WHERE tse.session.id = :sessionId AND tse.ticket.validityState = 'ACTIVE'")
    int countActiveConfirmedSeatsForSession(@Param("sessionId") Long sessionId);

    List<TicketSessionEntitlement> findBySessionIdAndAttendanceState(Long sessionId, AttendanceState state);
}
