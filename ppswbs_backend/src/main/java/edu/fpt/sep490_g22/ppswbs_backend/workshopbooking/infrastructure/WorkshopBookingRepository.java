package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.ConfirmationState;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopBookingRepository extends JpaRepository<WorkshopBooking, Long> {
    Optional<WorkshopBooking> findByBookingCode(String bookingCode);
    List<WorkshopBooking> findByCanonicalEmailAndConfirmationState(String canonicalEmail, ConfirmationState confirmationState);
    List<WorkshopBooking> findByCanonicalEmailAndConfirmationStateAndMemberIdIsNull(String canonicalEmail, ConfirmationState confirmationState);
}
