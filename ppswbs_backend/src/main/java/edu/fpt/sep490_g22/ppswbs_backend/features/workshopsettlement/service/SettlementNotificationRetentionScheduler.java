package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SettlementNotificationRetentionScheduler {
    private final SettlementNotificationHandoffService handoffs;
    private final SettlementNotificationEventDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${workshop.settlement.notification-retention-delay-ms:60000}")
    public void cleanExpiredNotificationPayloads() {
        Instant now = Instant.now();
        handoffs.deleteExpiredPayloads(now);
        dispatcher.dispatchPending();
    }
}
