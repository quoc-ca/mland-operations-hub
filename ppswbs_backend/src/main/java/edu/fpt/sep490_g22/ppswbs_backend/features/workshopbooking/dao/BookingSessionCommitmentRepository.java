package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingSessionCommitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingSessionCommitmentRepository extends JpaRepository<BookingSessionCommitment, Long> {

    List<BookingSessionCommitment> findByBookingIdOrderByOrdinalAsc(Long bookingId);

    Optional<BookingSessionCommitment> findByBookingIdAndOrdinal(Long bookingId, Integer ordinal);

    Optional<BookingSessionCommitment> findByBookingIdAndSessionId(Long bookingId, Long sessionId);
}
