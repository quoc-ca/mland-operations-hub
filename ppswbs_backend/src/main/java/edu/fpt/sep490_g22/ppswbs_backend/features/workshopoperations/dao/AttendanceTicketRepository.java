package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceTicketRepository extends JpaRepository<AttendanceTicket, Long> {

    Optional<AttendanceTicket> findByReference(String reference);

    Optional<AttendanceTicket> findByTicketCode(String ticketCode);

    Optional<AttendanceTicket> findByQrReference(String qrReference);

    List<AttendanceTicket> findByBookingReference(String bookingReference);

    boolean existsByTicketCode(String ticketCode);

    boolean existsByQrReference(String qrReference);
}
