package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRequestRepository extends JpaRepository<NotificationRequest, Long>, JpaSpecificationExecutor<NotificationRequest> {

    Optional<NotificationRequest> findByBusinessEventReferenceAndTemplateCodeAndRecipientRoleAndRecipientAddressFingerprintAndOriginalRequestIsNull(
            String businessEventReference,
            String templateCode,
            String recipientRole,
            String recipientAddressFingerprint
    );

    Optional<NotificationRequest> findTopByBusinessEventReferenceAndTemplateCodeOrderByCreatedAtDesc(
            String businessEventReference,
            String templateCode
    );

    @Query("SELECT r FROM NotificationRequest r WHERE " +
           "(r.deliveryState = 'PENDING' AND (r.nextAttemptAt IS NULL OR r.nextAttemptAt <= :now)) " +
           "OR (r.deliveryState = 'RETRY_SCHEDULED' AND r.nextAttemptAt <= :now) " +
           "ORDER BY r.id ASC")
    List<NotificationRequest> findProcessableRequests(@Param("now") Instant now, Pageable pageable);

    List<NotificationRequest> findByDeliveryStateIn(Collection<String> deliveryStates);

    List<NotificationRequest> findByDeliveryStateAndSmtpInvocationStartedAtIsNotNull(String deliveryState);

    List<NotificationRequest> findByTerminalAtBeforeAndDeliveryStateIn(Instant threshold, Collection<String> terminalStates);
}

