package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically processes Mland remedy offers that reached their 24h deadline without buyer answer,
 * defaulting them to a 100% bank refund (FR-043).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MlandRemedyAutoRefundScheduler {

    private final TicketMlandRemedyService remedyService;

    @Scheduled(fixedDelayString = "${workshop.settlement.remedy-auto-refund-interval-ms:60000}")
    public void scanAndDefaultUnansweredRemedies() {
        try {
            int count = remedyService.processUnansweredRemedies();
            if (count > 0) {
                log.info("Defaulted {} unanswered Mland remedies to refund", count);
            }
        } catch (Exception ex) {
            log.error("Error processing unanswered Mland remedies: {}", ex.getMessage(), ex);
        }
    }
}

