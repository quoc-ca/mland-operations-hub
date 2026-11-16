package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundReceiptImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundReceiptImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.SettlementReceiptStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 003.3 T154: removes receipt images that were uploaded but never attached to a recorded transfer within 24 hours
 * ({@code UPLOADED} to {@code DELETE_PENDING}, signed destroy, {@code DELETED}). An {@code ATTACHED} image is never
 * touched here, also after a return to the pending-account state or after {@code REFUNDED}: its deletion is the
 * 5-year job (T166, after V1). A destroy that fails leaves the row {@code DELETE_PENDING} and is retried.
 */
@Service
@Slf4j
public class RefundReceiptImageCleanupScheduler {

    private final SettlementRefundReceiptImageRepository images;
    private final RefundReceiptImageService service;
    private final SettlementReceiptStoragePort storage;

    public RefundReceiptImageCleanupScheduler(SettlementRefundReceiptImageRepository images,
                                              RefundReceiptImageService service,
                                              SettlementReceiptStoragePort storage) {
        this.images = images;
        this.service = service;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${workshop.settlement.receipt-image-cleanup.poll-delay-ms:900000}")
    public void run() {
        cleanUp(Instant.now());
    }

    /** Returns how many objects were destroyed in this pass. */
    public int cleanUp(Instant now) {
        int destroyed = 0;
        for (SettlementRefundReceiptImage stale : images.findTop100ByStateAndCreatedAtBeforeOrderByCreatedAtAsc(
                SettlementRefundReceiptImage.UPLOADED, now.minus(RefundReceiptImageService.UNATTACHED_RETENTION))) {
            try {
                service.markDeletePending(stale.getId(), now);
            } catch (RuntimeException failure) {
                log.warn("Receipt image {} could not be marked for deletion ({})", stale.getId(),
                        failure.getClass().getSimpleName());
            }
        }
        for (SettlementRefundReceiptImage pending : images.findTop100ByStateOrderByDeleteRequestedAtAsc(
                SettlementRefundReceiptImage.DELETE_PENDING)) {
            try {
                storage.deleteIfExists(pending.getStorageKey());
                service.markDeleted(pending.getId(), now);
                destroyed++;
            } catch (RuntimeException failure) {
                log.warn("Receipt image {} will be retried ({})", pending.getId(), failure.getClass().getSimpleName());
            }
        }
        return destroyed;
    }
}
