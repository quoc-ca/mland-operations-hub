package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.MediaReferenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deletes image objects nobody needs any more (T047, US4, FR-008/024/027).
 *
 * <ul>
 *   <li>Eligible: never attached for 24 hours, or without any active reference for 30 days. Any active
 *       reference (product, review snapshot, FE-08 or an unknown owner) or a product image row blocks
 *       deletion; deleting a product never releases its references, so its images stay as history.</li>
 *   <li>Per object: lock it, re-check eligibility, mark DELETE_PENDING and commit; then call the provider
 *       outside any transaction; then mark DELETED. A missing object counts as deleted. A temporary provider
 *       failure leaves DELETE_PENDING and the next run retries; attach/retain refuse an object in that state,
 *       so it is never brought back.</li>
 *   <li>Audit records only the media ID and state, never image content.</li>
 * </ul>
 */
@Service
public class CatalogueMediaCleanupService {

    public static final String ACTION = "media.cleanup";
    public static final Duration NEVER_ATTACHED_GRACE = Duration.ofHours(24);
    public static final Duration DETACHED_GRACE = Duration.ofDays(30);
    private static final int MAX_PER_RUN = 500;
    private static final Logger log = LoggerFactory.getLogger(CatalogueMediaCleanupService.class);

    public enum Result {
        DELETED,
        RETRY_LATER,
        SKIPPED
    }

    private final CatalogueMediaRepository media;
    private final MediaReferenceRepository references;
    private final ProductImageRepository images;
    private final MediaStoragePort storage;
    private final CatalogueAuditWriter audit;
    private final TransactionTemplate transaction;

    public CatalogueMediaCleanupService(CatalogueMediaRepository media, MediaReferenceRepository references,
                                        ProductImageRepository images, MediaStoragePort storage,
                                        CatalogueAuditWriter audit, TransactionTemplate transaction) {
        this.media = media;
        this.references = references;
        this.images = images;
        this.storage = storage;
        this.audit = audit;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelayString = "${catalogue.media.cleanup.delay:PT1H}",
            initialDelayString = "${catalogue.media.cleanup.initial-delay:PT10M}")
    public void cleanScheduled() {
        Map<Result, Long> results = cleanEligible(Instant.now()).stream()
                .collect(Collectors.groupingBy(result -> result, Collectors.counting()));
        if (!results.isEmpty()) {
            log.info("Catalogue media cleanup: {}", results);
        }
    }

    /** One run over the current candidates (at most 500); returns one result per candidate. */
    public List<Result> cleanEligible(Instant now) {
        Instant at = CatalogueValues.toStoredInstant(now);
        List<UUID> candidates = transaction.execute(status -> media.findCleanupCandidates(
                CatalogueMediaObject.State.DELETE_PENDING, CatalogueMediaObject.State.DELETED,
                at.minus(NEVER_ATTACHED_GRACE), at.minus(DETACHED_GRACE), PageRequest.of(0, MAX_PER_RUN)));
        return candidates.stream().map(id -> clean(id, at)).toList();
    }

    /** Cleans one object if it is (still) eligible or already being deleted. */
    public Result clean(UUID id, Instant at) {
        String storageKey = transaction.execute(status -> {
            CatalogueMediaObject object = lock(id);
            if (object == null || object.getState() == CatalogueMediaObject.State.DELETED) {
                return null;
            }
            if (object.getState() != CatalogueMediaObject.State.DELETE_PENDING) {
                if (!eligible(object, at)) {
                    return null;
                }
                object.requestDelete(at);
                media.saveAndFlush(object);
                audit.recordSuccess(new CatalogueAuditWriter.Entry(null, ACTION, "media", id.toString(), null, null,
                        List.of("state"), Map.of("state", CatalogueMediaObject.State.DELETE_PENDING.name())));
            }
            return object.getStorageKey();
        });
        if (storageKey == null) {
            return Result.SKIPPED;
        }
        try {
            storage.deleteIfExists(storageKey);
        } catch (CatalogueException e) {
            if (e.code() == CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE) {
                return Result.RETRY_LATER;
            }
            throw e;
        }
        transaction.executeWithoutResult(status -> {
            CatalogueMediaObject object = lock(id);
            if (object != null && object.getState() == CatalogueMediaObject.State.DELETE_PENDING) {
                object.markDeleted(at);
                audit.recordSuccess(new CatalogueAuditWriter.Entry(null, ACTION, "media", id.toString(), null, null,
                        List.of("state"), Map.of("state", CatalogueMediaObject.State.DELETED.name())));
            }
        });
        return Result.DELETED;
    }

    private boolean eligible(CatalogueMediaObject object, Instant at) {
        if (references.existsByMediaObjectIdAndReleasedAtIsNull(object.getId())
                || images.existsByMediaObjectId(object.getId())) {
            return false;
        }
        boolean neverAttached = object.getFirstAttachedAt() == null
                && !object.getCreatedAt().isAfter(at.minus(NEVER_ATTACHED_GRACE));
        boolean longDetached = object.getLastUnreferencedAt() != null
                && !object.getLastUnreferencedAt().isAfter(at.minus(DETACHED_GRACE));
        return neverAttached || longDetached;
    }

    private CatalogueMediaObject lock(UUID id) {
        List<CatalogueMediaObject> locked = media.lockAllByIdIn(List.of(id));
        return locked.isEmpty() ? null : locked.get(0);
    }
}
