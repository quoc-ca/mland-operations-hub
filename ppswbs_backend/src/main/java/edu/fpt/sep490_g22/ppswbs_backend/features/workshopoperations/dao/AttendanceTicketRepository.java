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

    /** A group ticket code (GRP-…-L2-T1) is the line reference plus the person's number (see GroupTicketCode). */
    Optional<AttendanceTicket> findByBookingReferenceAndTicketSequence(String bookingReference, Integer ticketSequence);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AttendanceTicket t where t.bookingReference = :bookingReference and t.ticketSequence = :ticketSequence")
    Optional<AttendanceTicket> findByBookingReferenceAndTicketSequenceWithLock(@Param("bookingReference") String bookingReference, @Param("ticketSequence") Integer ticketSequence);

    /** The ticket for a TK-… code, or else for a group ticket code. */
    default Optional<AttendanceTicket> findByAnyTicketCode(String code) {
        Optional<AttendanceTicket> own = findByTicketCode(code);
        if (own.isPresent()) return own;
        return edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.GroupTicketCode.parse(code)
                .flatMap(group -> findByBookingReferenceAndTicketSequence(group.bookingReference(), group.ticketSequence()));
    }

    default Optional<AttendanceTicket> findByAnyTicketCodeWithLock(String code) {
        Optional<AttendanceTicket> own = findByTicketCodeWithLock(code);
        if (own.isPresent()) return own;
        return edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.GroupTicketCode.parse(code)
                .flatMap(group -> findByBookingReferenceAndTicketSequenceWithLock(group.bookingReference(), group.ticketSequence()));
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AttendanceTicket t where t.reference = :reference")
    Optional<AttendanceTicket> findByReferenceWithLock(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AttendanceTicket t where t.ticketCode = :ticketCode")
    Optional<AttendanceTicket> findByTicketCodeWithLock(@Param("ticketCode") String ticketCode);

    Optional<AttendanceTicket> findByQrReference(String qrReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AttendanceTicket t where t.qrReference = :qrReference")
    Optional<AttendanceTicket> findByQrReferenceWithLock(@Param("qrReference") String qrReference);

    List<AttendanceTicket> findByBookingReference(String bookingReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AttendanceTicket t where t.bookingReference = :bookingReference or t.bookingReference like concat(:bookingReference, '-L%') order by t.bookingReference, t.ticketSequence")
    List<AttendanceTicket> findByBookingReferenceWithLock(@Param("bookingReference") String bookingReference);

    boolean existsByTicketCode(String ticketCode);

    boolean existsByQrReference(String qrReference);
}
