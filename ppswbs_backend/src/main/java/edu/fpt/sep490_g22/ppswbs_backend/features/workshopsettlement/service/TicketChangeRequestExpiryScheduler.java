package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChangeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Marks queued ticket-change requests whose time ran out as EXPIRED on time (FR-045a), instead of only when someone opens
 * the queue, so the customer's "expired" email goes out when it should. One transaction per request: a failure on one
 * never blocks the others.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketChangeRequestExpiryScheduler {

    private final SettlementTicketChangeRequestRepository requests;
    private final TicketChangeService changes;

    @Scheduled(fixedDelayString = "${workshop.settlement.change-request-expiry-interval-ms:60000}")
    public void expireDueRequests() {
        sweep(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    /** @return how many requests this sweep expired */
    int sweep(Instant now) {
        int expired = 0;
        try {
            for (SettlementTicketChangeRequest due
                    : requests.findByStatusAndExpiresAtLessThanEqual(SettlementTicketChangeRequest.PENDING, now)) {
                try {
                    if (changes.expireIfDue(due.getRequestReference(), now)) {
                        expired++;
                    }
                } catch (RuntimeException failure) {
                    log.error("Could not expire ticket change request {} ({})", due.getRequestReference(),
                            failure.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException failure) {
            log.error("Error scanning expired ticket change requests ({})", failure.getClass().getSimpleName());
        }
        if (expired > 0) {
            log.info("Expired {} ticket change request(s)", expired);
        }
        return expired;
    }
}
