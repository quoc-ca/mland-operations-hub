package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceTicketRepository extends JpaRepository<AttendanceTicket, Long> {

    Optional<AttendanceTicket> findByReference(String reference);

    Optional<AttendanceTicket> findByTicketCode(String ticketCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AttendanceTicket t where t.ticketCode = :ticketCode")
    Optional<AttendanceTicket> findByTicketCodeWithLock(@Param("ticketCode") String ticketCode);

    Optional<AttendanceTicket> findByQrReference(String qrReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AttendanceTicket t where t.qrReference = :qrReference")
    Optional<AttendanceTicket> findByQrReferenceWithLock(@Param("qrReference") String qrReference);

    List<AttendanceTicket> findByBookingReference(String bookingReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AttendanceTicket t where t.bookingReference = :bookingReference")
    List<AttendanceTicket> findByBookingReferenceWithLock(@Param("bookingReference") String bookingReference);

    boolean existsByTicketCode(String ticketCode);

    boolean existsByQrReference(String qrReference);
}
