package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHoldStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodic bounded scan; each checkout is independently locked and rechecked. */
@Component
@Profile("!local")
@ConditionalOnProperty(name = "retail.unstarted-expiry.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RetailUnstartedExpiryJob {
    private final RetailVariantHoldRepository holds;
    private final RetailUnstartedExpiryService expiry;

    @Scheduled(fixedDelayString = "${retail.unstarted-expiry.delay-ms:30000}")
    public void process() {
        var candidates = holds.findUnstartedExpiryCandidates(RetailVariantHoldStatus.HELD,
            PaymentState.ACCEPTED_UNPAID, Instant.now(), PageRequest.of(0, 100));
        for (var candidate : candidates) {
            try {
                expiry.expire(candidate.getMemberId(), candidate.getCheckoutId());
            } catch (RuntimeException exception) {
                log.warn("Retail unpaid expiry failed; will retry checkout {} (error type: {})",
                    candidate.getCheckoutId(), exception.getClass().getSimpleName());
            }
        }
    }
}
