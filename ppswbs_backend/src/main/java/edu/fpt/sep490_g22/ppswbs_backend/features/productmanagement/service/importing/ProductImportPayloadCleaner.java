package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImportBatchRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImportBatch;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Removes the payload and errors of import batches past their expiry and marks VALIDATED ones EXPIRED
 * (T100, FR-043/045). Metadata, counts, audit and receipts are kept (five years).
 *
 * <p>Each batch is handled in its own short transaction under the same batch lock as confirmation, so a
 * confirmation and the cleaner never both win; running it again or on several instances changes nothing more.
 */
@Component
public class ProductImportPayloadCleaner {

    private static final Logger log = LoggerFactory.getLogger(ProductImportPayloadCleaner.class);
    private static final int BATCH_SIZE = 100;

    private final ProductImportBatchRepository batches;
    private final TransactionTemplate transaction;

    public ProductImportPayloadCleaner(ProductImportBatchRepository batches, TransactionTemplate transaction) {
        this.batches = batches;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelayString = "${catalogue.import.cleanup.delay:PT5M}",
            initialDelayString = "${catalogue.import.cleanup.initial-delay:PT1M}")
    public void cleanExpired() {
        int cleaned = cleanExpired(Instant.now());
        if (cleaned > 0) {
            log.info("Cleaned {} expired product import batches", cleaned);
        }
    }

    /** Cleans every batch expired at {@code now}; returns how many batches changed. */
    public int cleanExpired(Instant now) {
        Instant at = CatalogueValues.toStoredInstant(now);
        int cleaned = 0;
        while (true) {
            List<UUID> ids = transaction.execute(status -> batches.findExpiredWithData(at,
                    ProductImportBatch.State.VALIDATED, PageRequest.of(0, BATCH_SIZE)));
            int changedInRound = 0;
            for (UUID id : ids) {
                Boolean changed = transaction.execute(status -> batches.lockById(id)
                        .map(batch -> batch.expire(at))
                        .orElse(false));
                if (Boolean.TRUE.equals(changed)) {
                    changedInRound++;
                }
            }
            cleaned += changedInRound;
            if (ids.size() < BATCH_SIZE || changedInRound == 0) {
                return cleaned;
            }
        }
    }
}
