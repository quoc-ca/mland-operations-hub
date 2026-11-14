package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {

    List<DeliveryAttempt> findByNotificationRequestIdOrderByAttemptNumberAsc(Long notificationRequestId);
}

