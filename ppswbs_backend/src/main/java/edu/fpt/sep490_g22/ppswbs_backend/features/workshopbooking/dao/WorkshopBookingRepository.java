package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopBookingRepository extends JpaRepository<WorkshopBooking, Long> {

    Optional<WorkshopBooking> findByBookingCode(String bookingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkshopBooking> findLockedByBookingCode(String bookingCode);

    List<WorkshopBooking> findByMemberAccountId(Long memberAccountId);

    List<WorkshopBooking> findByBookingStatusAndHoldExpiresAtBefore(BookingStatus bookingStatus, Instant cutoff);
}
