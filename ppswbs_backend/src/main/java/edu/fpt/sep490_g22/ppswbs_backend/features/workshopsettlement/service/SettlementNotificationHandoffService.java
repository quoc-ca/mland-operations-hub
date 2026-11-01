package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementNotificationHandoff;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementNotificationReady;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/** Persists the render-ready snapshot with its accepted Settlement outcome. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementNotificationHandoffService {
    private static final java.time.Duration PAYLOAD_RETENTION = java.time.Duration.ofDays(30);

    private final SettlementNotificationHandoffRepository handoffs;
    private final SettlementNotificationEventDispatcher dispatcher;

    @Transactional(propagation = Propagation.MANDATORY)
    public String create(String settlementReference, String notificationKind, String correlationReference,
                         String storedRecipient, String renderedContent) {
        require(settlementReference, "settlementReference");
        require(notificationKind, "notificationKind");
        require(correlationReference, "correlationReference");
        require(storedRecipient, "storedRecipient");
        require(renderedContent, "renderedContent");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Settlement notification handoff requires an active transaction");
        }
        Instant createdAt = Instant.now();
        Instant payloadExpiresAt = createdAt.plus(PAYLOAD_RETENTION);
        String notificationReference = "SETTLE-NOTIF-" + UUID.randomUUID();
        handoffs.saveAndFlush(SettlementNotificationHandoff.builder()
                .notificationReference(notificationReference)
                .settlementReference(settlementReference.trim())
                .notificationKind(notificationKind.trim())
                .correlationReference(correlationReference.trim())
                .storedRecipient(storedRecipient.trim())
                .renderedSettlementContent(renderedContent)
                .payloadExpiresAt(payloadExpiresAt)
                .createdAt(createdAt)
                .build());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dispatcher.dispatch(notificationReference);
                } catch (RuntimeException ex) {
                    // The durable pending handoff remains available to the retry scheduler.
                    log.warn("Settlement notification event dispatch failed: notificationReference={}",
                            notificationReference);
                }
            }
        });
        return notificationReference;
    }

    @Transactional
    public int deleteExpiredPayloads(Instant now) {
        return handoffs.deleteExpiredPayloads(now);
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
