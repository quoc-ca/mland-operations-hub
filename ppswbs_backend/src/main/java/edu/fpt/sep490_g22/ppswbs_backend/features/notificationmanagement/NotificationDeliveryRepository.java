package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    Optional<NotificationDelivery> findByNotificationReference(String notificationReference);

    List<NotificationDelivery> findTop50ByStatusAndAttemptsLessThanAndCreatedAtAfterOrderByCreatedAtAsc(
            String status, int attempts, Instant after);

    /** Takes a failed delivery for a retry; only one caller can win it. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationDelivery d set d.status = 'SENDING', d.updatedAt = :now "
            + "where d.id = :id and d.status = 'FAILED'")
    int claimForRetry(@Param("id") Long id, @Param("now") Instant now);
}
