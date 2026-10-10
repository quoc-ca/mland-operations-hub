package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkshopBookingRepository extends JpaRepository<WorkshopBooking, Long> {

    Optional<WorkshopBooking> findByBookingCode(String bookingCode);

    boolean existsByBookingCode(String bookingCode);

    long countBySessionIdAndBookingStatusIn(Long sessionId, java.util.List<BookingStatus> statuses);

    java.util.List<WorkshopBooking> findByCanonicalEmailAndBookingStatusAndMemberIdIsNull(String canonicalEmail, BookingStatus status);
}
