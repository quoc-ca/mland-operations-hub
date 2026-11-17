package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Retries, every minute, the confirmation of groups whose verified payment is kept in a {@code WAITING} row (003.1
 * T145). Each row is settled or retried in its own transactions, so one stuck dependency never blocks the others.
 * A retry is the idempotent {@code confirmPaidGroup}: a group that is already confirmed is never confirmed twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupConfirmationRetryScheduler {

    private final GroupConfirmationRetryService retries;
    private final GroupConfirmationAttemptRunner runner;

    @Scheduled(fixedDelayString = "${workshop.booking.group-confirmation-retry:PT1M}")
    public void retryWaitingConfirmations() {
        for (Long id : retries.dueIds(Instant.now())) {
            String correlation = "GROUP-CONFIRM-RETRY-" + UUID.randomUUID();
            try {
                if (retries.resolveIfFinal(id, correlation)) {
                    continue;
                }
                var row = retries.retryable(id).orElse(null);
                if (row == null) {
                    continue; // retries used up: the row waits for the hand-over at holdExpiresAt
                }
                var result = runner.tryConfirm(row.groupCode(), row.amountVnd(),
                        row.evidenceReference(), "RETRY-" + row.id(), correlation);
                retries.recordRetry(id, result.kind() == GroupConfirmationAttemptRunner.Kind.CONFIRMED,
                        result.failureCode());
            } catch (RuntimeException ex) {
                log.warn("Retry {} of a group confirmation failed: {}", id, ex.getClass().getSimpleName());
                try {
                    retries.recordRetry(id, false, ex.getClass().getSimpleName());
                } catch (RuntimeException ignored) {
                    log.warn("Could not record the failed retry {}", id);
                }
            }
        }
    }
}
