package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @Profile("retail-vnpay & !local") @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "retail.started-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class RetailConfirmedUnpaidExpiryJob {
    private final RetailVariantHoldRepository holds;
    private final RetailQueryReconciliationService reconciliation;

    @Scheduled(fixedDelayString = "${retail.started-expiry.delay-ms:30000}")
    public void process() {
        var candidates = holds.findStartedExpiryCandidates(RetailVariantHoldStatus.HELD,
                PaymentState.PAYMENT_INITIATED, Instant.now(), PageRequest.of(0, 100));
        for (var candidate : candidates) {
            try { reconciliation.reconcile(candidate.getMemberId(), candidate.getCheckoutId(), Instant.now()); }
            catch (RuntimeException ex) { log.warn("Retail started expiry deferred checkout {} ({})", candidate.getCheckoutId(), ex.getClass().getSimpleName()); }
        }
    }
}
