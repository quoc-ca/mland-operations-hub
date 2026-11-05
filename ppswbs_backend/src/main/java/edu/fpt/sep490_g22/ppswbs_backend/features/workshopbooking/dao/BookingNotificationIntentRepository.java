package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingNotificationIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingNotificationIntentRepository extends JpaRepository<BookingNotificationIntent, Long> {

    Optional<BookingNotificationIntent> findByNotificationReference(String notificationReference);

    List<BookingNotificationIntent> findByBookingId(Long bookingId);

    List<BookingNotificationIntent> findByCorrelationReference(String correlationReference);
}
