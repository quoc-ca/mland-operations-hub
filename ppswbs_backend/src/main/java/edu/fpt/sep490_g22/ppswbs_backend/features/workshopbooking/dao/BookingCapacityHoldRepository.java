package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingCapacityHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingCapacityHoldRepository extends JpaRepository<BookingCapacityHold, Long> {

    Optional<BookingCapacityHold> findByHoldReference(String holdReference);

    List<BookingCapacityHold> findByBookingId(Long bookingId);
}
