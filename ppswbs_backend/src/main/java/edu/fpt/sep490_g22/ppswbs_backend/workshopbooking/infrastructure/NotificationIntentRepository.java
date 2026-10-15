package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.NotificationIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationIntentRepository extends JpaRepository<NotificationIntent, Long> {

    List<NotificationIntent> findByBookingId(Long bookingId);

    /** Find all pending/retry intents due for retry. */
    List<NotificationIntent> findByStatusInAndNextRetryAtBefore(
            List<String> statuses, Instant now);
}
