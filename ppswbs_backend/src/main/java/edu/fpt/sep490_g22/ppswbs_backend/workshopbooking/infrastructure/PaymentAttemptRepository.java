package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.PaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByBookingId(Long bookingId);

    Optional<PaymentAttempt> findByProviderReference(String providerReference);

    Optional<PaymentAttempt> findTopByBookingIdAndStatusOrderByCreatedAtDesc(
            Long bookingId, PaymentAttemptStatus status);
}
