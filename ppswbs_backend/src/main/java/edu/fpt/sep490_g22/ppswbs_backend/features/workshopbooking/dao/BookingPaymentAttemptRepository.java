package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingPaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingPaymentAttemptRepository extends JpaRepository<BookingPaymentAttempt, Long> {

    Optional<BookingPaymentAttempt> findByAttemptReference(String attemptReference);

    Optional<BookingPaymentAttempt> findByMerchantReference(String merchantReference);

    List<BookingPaymentAttempt> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
