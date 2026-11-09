package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryService {

    private final NotificationRequestRepository notificationRequestRepository;

    /**
     * Recovers interrupted requests after process crash or restart (FR-014).
     * Any request left in PROCESSING where SMTP invocation may have begun is transitioned to UNKNOWN_OUTCOME.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        recoverInterruptedRequests();
    }

    @Transactional
    public int recoverInterruptedRequests() {
        List<NotificationRequest> processingRequests = notificationRequestRepository.findByDeliveryStateIn(List.of("PROCESSING"));
        if (processingRequests.isEmpty()) {
            return 0;
        }

        int recoveredCount = 0;
        for (NotificationRequest request : processingRequests) {
            if (request.getSmtpInvocationStartedAt() != null) {
                log.warn("Recovery: Request {} was interrupted during or after SMTP invocation. Transitioning to UNKNOWN_OUTCOME.", request.getId());
                request.setDeliveryState("UNKNOWN_OUTCOME");
                recoveredCount++;
            } else {
                log.info("Recovery: Request {} was interrupted before SMTP invocation started. Resetting to PENDING.", request.getId());
                request.setDeliveryState("PENDING");
                recoveredCount++;
            }
            notificationRequestRepository.save(request);
        }
        return recoveredCount;
    }
}

