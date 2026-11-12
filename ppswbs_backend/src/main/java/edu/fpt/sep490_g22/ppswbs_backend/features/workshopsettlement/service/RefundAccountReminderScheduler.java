package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reminds a payer on day 3 and day 7 that Mland still needs their account to send a refund back
 * (003.3 FR-040). Each reminder goes out once: the day it was sent is stored on the request, so a restart
 * or a second sweep never repeats it. After 30 days a Manager may close the request as unreachable, which
 * is a decision and not something this sweep does on its own; the customer never loses the right to the
 * money.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundAccountReminderScheduler {

    private static final Duration FIRST_REMINDER = Duration.ofDays(3);
    private static final Duration SECOND_REMINDER = Duration.ofDays(7);

    private final SettlementRefundRequestRepository requests;
    private final SettlementCustomerNotifier notifier;

    @Scheduled(fixedDelayString = "${workshop.settlement.refund-reminder-sweep:PT1H}")
    @Transactional
    public void remindPayersWithoutAnAccount() {
        Instant now = Instant.now();
        List<SettlementRefundRequest> waiting = requests.findUnansweredSince(now.minus(FIRST_REMINDER));
        for (SettlementRefundRequest request : waiting) {
            try {
                remind(request, now);
            } catch (RuntimeException failure) {
                log.warn("Could not remind the payer of refund {}: {}", request.getRequestReference(),
                        failure.getMessage());
            }
        }
    }

    private void remind(SettlementRefundRequest request, Instant now) {
        Instant openedAt = request.getReopenedAt() != null ? request.getReopenedAt() : request.getCreatedAt();
        boolean dueSecond = !openedAt.plus(SECOND_REMINDER).isAfter(now) && request.getReminderDay7At() == null;
        boolean dueFirst = !dueSecond && !openedAt.plus(FIRST_REMINDER).isAfter(now)
                && request.getReminderDay3At() == null;
        if (!dueFirst && !dueSecond) {
            return;
        }
        notifier.notifyRefundAccountNeeded(request.getBookingReference(), request.getAmountVnd(),
                request.getRequestReference(), "REFUND-REMINDER-" + UUID.randomUUID());
        if (dueSecond) {
            request.setReminderDay7At(now);
            if (request.getReminderDay3At() == null) {
                // The first reminder was missed, for example while the service was down; it is not sent late.
                request.setReminderDay3At(now);
            }
        } else {
            request.setReminderDay3At(now);
        }
        requests.saveAndFlush(request);
    }
}
