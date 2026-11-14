package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementNotificationHandoff;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementNotificationReady;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Retryable event outbox publisher. Replays are safe because Feature 007 keys by notificationReference. */
@Service
@RequiredArgsConstructor
public class SettlementNotificationEventDispatcher {
    private final SettlementNotificationHandoffRepository handoffs;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(String notificationReference) {
        SettlementNotificationHandoff handoff = handoffs
                .findByNotificationReference(notificationReference).orElse(null);
        if (handoff == null || handoff.getDispatchedAt() != null
                || !handoff.getPayloadExpiresAt().isAfter(Instant.now())) return;

        eventPublisher.publishEvent(new SettlementNotificationReady(handoff.getNotificationReference(),
                handoff.getSettlementReference(), handoff.getNotificationKind(), handoff.getCorrelationReference()));
        handoff.setDispatchedAt(Instant.now());
        handoffs.save(handoff);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatchPending() {
        int published = 0;
        for (SettlementNotificationHandoff handoff : handoffs
                .findTop100ByDispatchedAtIsNullAndPayloadExpiresAtAfterOrderByCreatedAtAsc(Instant.now())) {
            eventPublisher.publishEvent(new SettlementNotificationReady(handoff.getNotificationReference(),
                    handoff.getSettlementReference(), handoff.getNotificationKind(), handoff.getCorrelationReference()));
            handoff.setDispatchedAt(Instant.now());
            handoffs.save(handoff);
            published++;
        }
        return published;
    }
}
