package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.ExternalRegistrationHandoff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExternalRegistrationHandoffRepository extends JpaRepository<ExternalRegistrationHandoff, Long> {

    Optional<ExternalRegistrationHandoff> findByBookingIdAndEventIdentity(Long bookingId, String eventIdentity);

    boolean existsByBookingIdAndEventIdentity(Long bookingId, String eventIdentity);
}
