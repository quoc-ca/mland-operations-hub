package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupNotificationIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingGroupNotificationIntentRepository extends JpaRepository<BookingGroupNotificationIntent, Long> {

    Optional<BookingGroupNotificationIntent> findByNotificationReference(String notificationReference);

    List<BookingGroupNotificationIntent> findByGroupIdOrderByCreatedAtAsc(Long groupId);

    @Modifying
    @Query("update BookingGroupNotificationIntent i set i.deliverySnapshot = null "
            + "where i.snapshotExpiresAt is not null and i.snapshotExpiresAt <= :now and i.deliverySnapshot is not null")
    int scrubExpiredSnapshots(@Param("now") Instant now);
}
