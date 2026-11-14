package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaInventoryAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaInventoryAdapterRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaOutboundStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaSyncDeliveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaDeliveryAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChangeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * 003.4 T105 (FR-004a, FR-004c, FR-017): sends every accepted outbound inventory change to its provider.
 *
 * <p>Each poll picks the oldest {@code QUEUED} or {@code RETRYABLE} changes. A change is delivered in its own
 * transaction that first locks the row and re-reads its status, so overlapping runs or several instances never send
 * the same change twice. The provider adapter decides the outcome of one attempt; the result is recorded through
 * {@link OtaSyncDeliveryService#recordDeliveryResult}, which appends the attempt, completes preparation sync after a
 * confirmed snapshot and, on {@code EXHAUSTED}, suspends the mapping (sales closed) and opens a reconciliation work
 * item. A change never retries forever: once {@code ota.outbound-delivery.max-attempts} attempts are used, a further
 * {@code RETRYABLE} answer is recorded as {@code EXHAUSTED}. No numeric retry or latency target is approved
 * (plan: Performance Goals), so the poll delay and attempt cap are configuration.
 */
@Component
@Slf4j
public class OtaOutboundDeliveryWorker {

    private static final List<String> PENDING = List.of(OtaOutboundStatus.QUEUED.name(),
            OtaOutboundStatus.RETRYABLE.name());

    private final OtaOutboundChangeRepository changes;
    private final OtaDeliveryAttemptRepository attempts;
    private final OtaInventoryAdapterRegistry adapters;
    private final OtaSyncDeliveryService deliveries;
    private final TransactionTemplate transaction;
    private final int maxAttempts;

    public OtaOutboundDeliveryWorker(OtaOutboundChangeRepository changes, OtaDeliveryAttemptRepository attempts,
                                     OtaInventoryAdapterRegistry adapters, OtaSyncDeliveryService deliveries,
                                     PlatformTransactionManager transactionManager,
                                     @Value("${ota.outbound-delivery.max-attempts:3}") int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("ota.outbound-delivery.max-attempts must be at least 1");
        }
        this.changes = changes;
        this.attempts = attempts;
        this.adapters = adapters;
        this.deliveries = deliveries;
        this.transaction = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
    }

    /** Delivers the current batch of pending changes; returns how many attempts were recorded. */
    @Scheduled(fixedDelayString = "${ota.outbound-delivery.poll-delay-ms:30000}")
    public int deliverPending() {
        int recorded = 0;
        for (OtaOutboundChange pending : changes.findTop50ByStatusInOrderByCreatedAtAscIdAsc(PENDING)) {
            try {
                Boolean done = transaction.execute(status -> deliverOne(pending.getId()));
                if (Boolean.TRUE.equals(done)) {
                    recorded++;
                }
            } catch (RuntimeException ex) {
                // One broken change must not stop the batch; it stays pending and is tried on the next poll.
                log.warn("OTA outbound change {} could not be delivered this run: {}", pending.getId(),
                        ex.getClass().getSimpleName());
            }
        }
        return recorded;
    }

    private boolean deliverOne(Long changeId) {
        OtaOutboundChange change = changes.findByIdForUpdate(changeId).orElse(null);
        if (change == null || !PENDING.contains(change.getStatus())) {
            return false; // delivered or exhausted by another run meanwhile
        }
        OtaChannelMapping mapping = change.getMapping();
        int attemptNumber = (int) attempts.countByOutboundChangeId(changeId) + 1;
        OtaDeliveryResult result = adapters.findAdapter(mapping.getConnection().getProviderCode())
                .map(adapter -> send(adapter, mapping, change, attemptNumber))
                .orElseGet(() -> exhausted(attemptNumber, "NO_ADAPTER"));
        if (result.outcome() == OtaDeliveryOutcome.RETRYABLE && attemptNumber >= maxAttempts) {
            result = new OtaDeliveryResult(result.attemptNumber(), result.startedAt(), result.completedAt(),
                    OtaDeliveryOutcome.EXHAUSTED, result.correlationReference(), "RETRIES_EXHAUSTED");
        }
        deliveries.recordDeliveryResult(changeId, result);
        return true;
    }

    private static OtaDeliveryResult send(OtaInventoryAdapter adapter, OtaChannelMapping mapping,
                                          OtaOutboundChange change, int attemptNumber) {
        Instant startedAt = Instant.now();
        try {
            return adapter.deliver(mapping, change, attemptNumber);
        } catch (RuntimeException ex) {
            return new OtaDeliveryResult(attemptNumber, startedAt, Instant.now(), OtaDeliveryOutcome.RETRYABLE,
                    null, "ADAPTER_ERROR");
        }
    }

    private static OtaDeliveryResult exhausted(int attemptNumber, String category) {
        Instant now = Instant.now();
        return new OtaDeliveryResult(attemptNumber, now, now, OtaDeliveryOutcome.EXHAUSTED, null, category);
    }
}
