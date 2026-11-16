package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundReceiptImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundReceiptImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.ReceiptImageContentInspector;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.SettlementReceiptException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.SettlementReceiptStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The optional receipt image of a recorded refund transfer (003.3 T154, FR-039, plan O-1). Upload is separate from
 * {@code RECORD_TRANSFER}; the transfer attaches at most one uploaded image of the same request in its own
 * transaction and still succeeds without one. Storage is never touched inside a database transaction that holds a
 * refund lock, so an unavailable provider cannot block recording a transfer.
 */
@Service
@Slf4j
public class RefundReceiptImageService {

    /** An upload never attached after this long is removed (same rule as 002). */
    public static final java.time.Duration UNATTACHED_RETENTION = java.time.Duration.ofHours(24);

    private final SettlementRefundReceiptImageRepository images;
    private final SettlementRefundRequestRepository requests;
    private final SettlementReceiptStoragePort storage;
    private final BankRefundService bankRefunds;

    public RefundReceiptImageService(SettlementRefundReceiptImageRepository images,
                                     SettlementRefundRequestRepository requests,
                                     SettlementReceiptStoragePort storage,
                                     BankRefundService bankRefunds) {
        this.images = images;
        this.requests = requests;
        this.storage = storage;
        this.bankRefunds = bankRefunds;
    }

    /** Metadata of an uploaded image; never a provider URL. */
    public record ReceiptImageView(String receiptImageId, String contentType, long byteCount, String checksum,
                                   Instant uploadedAt) {
        static ReceiptImageView of(SettlementRefundReceiptImage image) {
            return new ReceiptImageView(image.getId(), image.getContentType(), image.getByteCount(),
                    image.getChecksum(), image.getCreatedAt());
        }
    }

    /** The bytes of the attached image, read through the backend. */
    public record ReceiptContent(String contentType, byte[] bytes) {
    }

    /**
     * Uploads the image of a request that is waiting for its transfer. A replay of the same key and the same bytes
     * answers the first result; the same key with other bytes is {@code 409 IDEMPOTENCY_KEY_REUSED}.
     */
    public ReceiptImageView upload(String refundReference, Long actorAccountId, String idempotencyKey,
                                   String declaredContentType, byte[] bytes) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_REQUIRED");
        }
        String key = idempotencyKey.trim();
        SettlementRefundRequest request = requests.findByRequestReference(refundReference)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        String checksum = sha256(bytes == null ? new byte[0] : bytes);
        Optional<SettlementRefundReceiptImage> replay =
                images.findByUploadedByAccountIdAndUploadIdempotencyKey(actorAccountId, key);
        if (replay.isPresent()) {
            return replayOf(replay.get(), request, checksum);
        }
        if (request.getStatus() != RefundRequestStatus.PENDING_TRANSFER) {
            throw new WorkshopSettlementConflictException("REFUND_NOT_AWAITING_TRANSFER");
        }
        String contentType = ReceiptImageContentInspector.verify(declaredContentType, bytes);

        UUID imageId = UUID.randomUUID();
        String storageKey = storage.storageKeyFor(imageId);
        SettlementReceiptStoragePort.StoredObject stored = storage.put(storageKey, contentType, bytes);
        try {
            SettlementRefundReceiptImage saved = images.saveAndFlush(SettlementRefundReceiptImage.builder()
                    .id(imageId.toString())
                    .refundRequestId(request.getId())
                    .storageKey(storageKey)
                    .providerAssetId(stored.providerAssetId())
                    .providerVersion(stored.providerVersion())
                    .providerFormat(stored.providerFormat())
                    .checksum(checksum)
                    .contentType(contentType)
                    .byteCount(bytes.length)
                    .state(SettlementRefundReceiptImage.UPLOADED)
                    .uploadedByAccountId(actorAccountId)
                    .uploadIdempotencyKey(key)
                    .createdAt(Instant.now())
                    .build());
            return ReceiptImageView.of(saved);
        } catch (DataIntegrityViolationException concurrentReplay) {
            deleteQuietly(storageKey);
            return images.findByUploadedByAccountIdAndUploadIdempotencyKey(actorAccountId, key)
                    .map(existing -> replayOf(existing, request, checksum))
                    .orElseThrow(() -> concurrentReplay);
        } catch (RuntimeException saveFailure) {
            deleteQuietly(storageKey);
            throw saveFailure;
        }
    }

    /**
     * {@code RECORD_TRANSFER} with an image: the transfer and the attachment commit together or not at all. The
     * refund row is locked by the transfer; the image row is locked here so the 24 hour cleanup cannot take it away.
     */
    @Transactional
    public SettlementRefundRequest recordTransferWithImage(String refundReference, Long actorAccountId,
                                                            String bankTransferReference, String proofReference,
                                                            String receiptImageId, String correlationReference) {
        SettlementRefundRequest recorded = bankRefunds.recordTransfer(refundReference, actorAccountId,
                bankTransferReference, proofReference, correlationReference);
        attach(recorded, receiptImageId, actorAccountId);
        return recorded;
    }

    private void attach(SettlementRefundRequest request, String receiptImageId, Long actorAccountId) {
        SettlementRefundReceiptImage image = images.findForUpdate(receiptImageId.trim())
                .filter(found -> found.getRefundRequestId().equals(request.getId()))
                .orElseThrow(() -> new SettlementUnprocessableException("RECEIPT_IMAGE_REFERENCE_INVALID"));
        if (SettlementRefundReceiptImage.ATTACHED.equals(image.getState())) {
            throw new WorkshopSettlementConflictException("RECEIPT_IMAGE_ALREADY_ATTACHED");
        }
        if (!SettlementRefundReceiptImage.UPLOADED.equals(image.getState())) {
            throw new SettlementUnprocessableException("RECEIPT_IMAGE_REFERENCE_INVALID");
        }
        String reference = request.getBankTransferReference();
        if (images.existsByRefundRequestIdAndAttachedBankTransferReferenceAndState(request.getId(), reference,
                SettlementRefundReceiptImage.ATTACHED)) {
            throw new WorkshopSettlementConflictException("RECEIPT_IMAGE_ALREADY_ATTACHED");
        }
        image.setState(SettlementRefundReceiptImage.ATTACHED);
        image.setAttachedAt(Instant.now());
        image.setAttachedByAccountId(actorAccountId);
        image.setAttachedBankTransferReference(reference);
        images.saveAndFlush(image);
    }

    /** The image of the latest recorded transfer, through the backend; 404 when none is attached or it was deleted. */
    public ReceiptContent read(String refundReference) {
        SettlementRefundRequest request = requests.findByRequestReference(refundReference)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementRefundReceiptImage image = images
                .findFirstByRefundRequestIdAndStateOrderByAttachedAtDesc(request.getId(),
                        SettlementRefundReceiptImage.ATTACHED)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        Optional<InputStream> opened = storage.open(new SettlementReceiptStoragePort.StoredObject(
                image.getStorageKey(), image.getProviderAssetId(), image.getProviderVersion(),
                image.getProviderFormat()));
        try (InputStream stream = opened.orElseThrow(WorkshopSettlementNotFoundException::new)) {
            return new ReceiptContent(image.getContentType(),
                    stream.readNBytes(ReceiptImageContentInspector.MAX_BYTES + 1));
        } catch (IOException readFailure) {
            log.warn("Receipt image read failed ({})", readFailure.getClass().getSimpleName());
            throw new SettlementReceiptException(SettlementReceiptException.Failure.STORAGE_UNAVAILABLE);
        }
    }

    /** The ids of the given requests that have an attached image, for the queue's {@code receiptImageAttached}. */
    @Transactional(readOnly = true)
    public Set<Long> requestIdsWithAttachedImage(Collection<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return Set.of();
        }
        return images.findRequestIdsWithAttachedImage(requestIds).stream().collect(Collectors.toSet());
    }

    // ---- cleanup of never-attached uploads (driven by RefundReceiptImageCleanupScheduler) ----

    /** UPLOADED to DELETE_PENDING, unless an attach got there first. Returns the storage key to destroy. */
    @Transactional
    public Optional<String> markDeletePending(String imageId, Instant now) {
        return images.findForUpdate(imageId)
                .filter(image -> SettlementRefundReceiptImage.UPLOADED.equals(image.getState())
                        && image.getCreatedAt().isBefore(now.minus(UNATTACHED_RETENTION)))
                .map(image -> {
                    image.setState(SettlementRefundReceiptImage.DELETE_PENDING);
                    image.setDeleteRequestedAt(now);
                    images.saveAndFlush(image);
                    return image.getStorageKey();
                });
    }

    @Transactional
    public void markDeleted(String imageId, Instant now) {
        images.findForUpdate(imageId)
                .filter(image -> SettlementRefundReceiptImage.DELETE_PENDING.equals(image.getState()))
                .ifPresent(image -> {
                    image.setState(SettlementRefundReceiptImage.DELETED);
                    image.setDeletedAt(now);
                    images.saveAndFlush(image);
                });
    }

    private ReceiptImageView replayOf(SettlementRefundReceiptImage existing, SettlementRefundRequest request,
                                      String checksum) {
        if (!existing.getRefundRequestId().equals(request.getId()) || !existing.getChecksum().equals(checksum)) {
            throw new WorkshopSettlementConflictException("IDEMPOTENCY_KEY_REUSED");
        }
        return ReceiptImageView.of(existing);
    }

    private void deleteQuietly(String storageKey) {
        try {
            storage.deleteIfExists(storageKey);
        } catch (RuntimeException ignored) {
            log.warn("An orphaned receipt object could not be removed; the cleanup cannot see it");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
