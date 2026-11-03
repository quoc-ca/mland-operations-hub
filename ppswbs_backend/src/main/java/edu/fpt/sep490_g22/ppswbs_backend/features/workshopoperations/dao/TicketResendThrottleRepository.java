package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketResendThrottle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TicketResendThrottleRepository extends JpaRepository<TicketResendThrottle, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketResendThrottle t where t.bookingReference = :bookingReference")
    Optional<TicketResendThrottle> findByBookingReferenceWithLock(@Param("bookingReference") String bookingReference);
}
