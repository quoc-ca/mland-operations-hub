package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaInventoryAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaInventoryAdapterRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaSalesControlService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaControlOutboxCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaControlOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

/**
 * 003.4 T120 (FR-004a, FR-004c, contract "Outbound: close/reopen sale"): sends every queued Manager sales-close
 * command to its provider.
 *
 * <p>Each poll picks the oldest {@code QUEUED} commands. A command is handled in its own transaction that first locks
 * the row and re-reads its status, so overlapping runs or several instances never send it twice. The control outbox
 * has no attempt table, so the adapter's retry policy runs inside that one transaction: up to
 * {@code ota.outbound-delivery.max-attempts} attempts, stopping at the first final answer. A command therefore always
 * ends {@code DELIVERED} or {@code EXHAUSTED}; it never retries forever. The result is recorded through
 * {@link OtaSalesControlService#recordSalesCloseDelivery}.
 */
@Component
@Slf4j
public class OtaControlOutboxDeliveryWorker {

    private static final String QUEUED = "QUEUED";
    private static final String SALES_CLOSE = "SALES_CLOSE";

    private final OtaControlOutboxRepository commands;
    private final OtaInventoryAdapterRegistry adapters;
    private final OtaSalesControlService salesControl;
    private final TransactionTemplate transaction;
    private final int maxAttempts;

    public OtaControlOutboxDeliveryWorker(OtaControlOutboxRepository commands, OtaInventoryAdapterRegistry adapters,
                                          OtaSalesControlService salesControl,
                                          PlatformTransactionManager transactionManager,
                                          @Value("${ota.outbound-delivery.max-attempts:3}") int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("ota.outbound-delivery.max-attempts must be at least 1");
        }
        this.commands = commands;
        this.adapters = adapters;
        this.salesControl = salesControl;
        this.transaction = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
    }

    /** Delivers the current batch of queued control commands; returns how many reached a final state. */
    @Scheduled(fixedDelayString = "${ota.outbound-delivery.poll-delay-ms:30000}")
    public int deliverPending() {
        int recorded = 0;
        for (OtaControlOutboxCommand pending : commands.findTop50ByStatusOrderByCreatedAtAscIdAsc(QUEUED)) {
            try {
                Boolean done = transaction.execute(status -> deliverOne(pending.getId()));
                if (Boolean.TRUE.equals(done)) {
                    recorded++;
                }
            } catch (RuntimeException ex) {
                // One broken command must not stop the batch; it stays queued and is tried on the next poll.
                log.warn("OTA control command {} could not be delivered this run: {}", pending.getId(),
                        ex.getClass().getSimpleName());
            }
        }
        return recorded;
    }

    private boolean deliverOne(Long commandId) {
        OtaControlOutboxCommand command = commands.findByIdForUpdate(commandId).orElse(null);
        if (command == null || !QUEUED.equals(command.getStatus())) {
            return false; // handled by another run meanwhile
        }
        if (!SALES_CLOSE.equals(command.getCommandKind())) {
            salesControl.recordSalesCloseDelivery(command, exhausted(1, "UNSUPPORTED_COMMAND"));
            return true;
        }
        OtaChannelMapping mapping = command.getMapping();
        Optional<OtaInventoryAdapter> adapter = adapters.findAdapter(mapping.getConnection().getProviderCode());
        OtaDeliveryResult result;
        if (adapter.isEmpty()) {
            result = exhausted(1, "NO_ADAPTER");
        } else {
            result = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                result = send(adapter.get(), mapping, command.getOperationReference(), attempt);
                if (result.outcome() != OtaDeliveryOutcome.RETRYABLE) {
                    break;
                }
            }
            if (result.outcome() == OtaDeliveryOutcome.RETRYABLE) {
                result = new OtaDeliveryResult(result.attemptNumber(), result.startedAt(), result.completedAt(),
                        OtaDeliveryOutcome.EXHAUSTED, result.correlationReference(), "RETRIES_EXHAUSTED");
            }
        }
        salesControl.recordSalesCloseDelivery(command, result);
        return true;
    }

    private static OtaDeliveryResult send(OtaInventoryAdapter adapter, OtaChannelMapping mapping,
                                          String operationReference, int attemptNumber) {
        Instant startedAt = Instant.now();
        try {
            OtaDeliveryResult result = adapter.deliverSalesClose(mapping, operationReference, attemptNumber);
            return result != null ? result
                    : new OtaDeliveryResult(attemptNumber, startedAt, Instant.now(), OtaDeliveryOutcome.RETRYABLE,
                    null, "ADAPTER_ERROR");
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
