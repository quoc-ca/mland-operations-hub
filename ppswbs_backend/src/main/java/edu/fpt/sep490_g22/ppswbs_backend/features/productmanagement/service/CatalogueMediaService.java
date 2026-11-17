package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Media staging (T016, FR-008/026/028): one verified JPEG/PNG/WebP file per request, at most
 * 5,000,000 bytes.
 *
 * <ol>
 *   <li>Verify type, signature and header ({@link ImageContentInspector}) and compute the SHA-256.</li>
 *   <li>Reserve a STAGED object for actor + command key + checksum in its own transaction. The same key
 *       with a different file is {@code IDEMPOTENCY_KEY_REUSED}.</li>
 *   <li>Upload outside any transaction or lock. A timeout leaves the object STAGED; retrying the same key
 *       reconciles with the provider instead of creating a second object.</li>
 *   <li>Mark READY only after the provider confirmed the content, together with audit and receipt.</li>
 * </ol>
 * The result never contains a provider URL. Attaching to a product is a separate operation.
 */
@Service
public class CatalogueMediaService {

    public static final String UPLOAD_ACTION = "media.upload";
    public static final String VERIFY_ACTION = "media.verify";

    public record UploadResult(UUID mediaId, String state, String contentType, long byteCount) {
    }

    private final CatalogueMediaRepository media;
    private final MediaStoragePort storage;
    private final CatalogueCommandExecutor executor;
    private final CatalogueAuditWriter audit;
    private final TransactionTemplate transaction;

    public CatalogueMediaService(CatalogueMediaRepository media, MediaStoragePort storage,
                                 CatalogueCommandExecutor executor, CatalogueAuditWriter audit,
                                 PlatformTransactionManager transactionManager) {
        this.media = media;
        this.storage = storage;
        this.executor = executor;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** The caller has authorized {@code actorId} for media management before calling. */
    public UploadResult upload(String actorId, UUID commandId, String declaredContentType, byte[] bytes) {
        String contentType = ImageContentInspector.verify(declaredContentType, bytes);
        String checksum = sha256(bytes);
        CatalogueCommandExecutor.CommandKey key =
                new CatalogueCommandExecutor.CommandKey(actorId, UPLOAD_ACTION, commandId, checksum);

        Optional<CatalogueCommandExecutor.Outcome> committed = executor.findCommitted(key);
        if (committed.isPresent()) {
            return fromBody(committed.get().body());
        }

        CatalogueMediaObject staged = reserve(actorId, commandId, checksum, contentType, bytes.length);
        MediaStoragePort.StoredObject stored = storage.putIfAbsent(staged.getStorageKey(),
                new MediaStoragePort.VerifiedContent(bytes, contentType, checksum));

        CatalogueCommandExecutor.Outcome outcome = executor.execute(key, () -> {
            CatalogueMediaObject locked = media.lockAllByIdIn(List.of(staged.getId())).stream().findFirst()
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND));
            if (locked.getState() == CatalogueMediaObject.State.STAGED
                    || locked.getState() == CatalogueMediaObject.State.UNAVAILABLE) {
                locked.markReady(stored.providerAssetId(), stored.providerVersion(), stored.providerFormat(),
                        now());
            } else if (locked.getState() != CatalogueMediaObject.State.READY) {
                throw new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND);
            }
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, UPLOAD_ACTION, "media",
                    locked.getId().toString(), null, null, List.of("state"),
                    Map.of("contentType", contentType, "byteCount", locked.getByteCount())));
            UploadResult result = new UploadResult(locked.getId(), "READY", contentType, locked.getByteCount());
            return new CatalogueCommandExecutor.CommandResult(201, "media", locked.getId().toString(), null, result);
        });
        return fromBody(outcome.body());
    }

    private CatalogueMediaObject reserve(String actorId, UUID commandId, String checksum, String contentType,
                                         long byteCount) {
        return transaction.execute(status -> {
            Optional<CatalogueMediaObject> existing = media.findByUploadedByAndCommandId(actorId, commandId);
            if (existing.isPresent()) {
                if (!existing.get().getChecksum().equals(checksum)) {
                    throw new CatalogueException(CatalogueErrorCode.IDEMPOTENCY_KEY_REUSED);
                }
                return existing.get();
            }
            UUID id = UUID.randomUUID();
            return media.saveAndFlush(CatalogueMediaObject.stage(id, storage.storageKeyFor(id), checksum,
                    contentType, byteCount, actorId, commandId, now()));
        });
    }

    /**
     * Re-checks one READY or UNAVAILABLE object at the provider's Admin API and records the result (T073, FR-036):
     * a missing object becomes UNAVAILABLE (its products leave the public catalogue, publication flags and history
     * stay), a readable UNAVAILABLE object becomes READY again (eligibility comes back when the other conditions
     * hold). A provider failure changes nothing and surfaces as {@code MEDIA_DEPENDENCY_UNAVAILABLE}: a timeout is
     * never taken as "missing". The provider is called outside any transaction or lock.
     */
    public CatalogueMediaObject.State verify(UUID mediaId) {
        CatalogueMediaObject current = transaction.execute(status -> media.findById(mediaId)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND)));
        if (current.getState() != CatalogueMediaObject.State.READY
                && current.getState() != CatalogueMediaObject.State.UNAVAILABLE) {
            return current.getState();
        }
        MediaStoragePort.Availability availability = storage.verifyReadable(new MediaStoragePort.StoredObject(
                current.getStorageKey(), current.getProviderAssetId(), current.getProviderVersion(),
                current.getProviderFormat()));
        return transaction.execute(status -> {
            CatalogueMediaObject locked = media.lockAllByIdIn(List.of(mediaId)).get(0);
            Instant now = now();
            CatalogueMediaObject.State before = locked.getState();
            if (availability == MediaStoragePort.Availability.MISSING && before == CatalogueMediaObject.State.READY) {
                locked.markUnavailable(now);
            } else if (availability == MediaStoragePort.Availability.READABLE
                    && before == CatalogueMediaObject.State.UNAVAILABLE) {
                locked.markReady(locked.getProviderAssetId(), locked.getProviderVersion(), locked.getProviderFormat(), now);
            } else {
                return before;
            }
            audit.recordSuccess(new CatalogueAuditWriter.Entry(null, VERIFY_ACTION, "media", mediaId.toString(), null,
                    null, List.of("state"), Map.of("from", before.name(), "to", locked.getState().name())));
            return locked.getState();
        });
    }

    /** Re-checks UNAVAILABLE objects so restored images come back without anyone re-uploading them. */
    @Scheduled(fixedDelayString = "${catalogue.media.verify.delay:PT30M}",
            initialDelayString = "${catalogue.media.verify.initial-delay:PT5M}")
    public void reverifyUnavailable() {
        List<UUID> ids = transaction.execute(status -> media.findIdsByState(CatalogueMediaObject.State.UNAVAILABLE,
                PageRequest.of(0, 100)));
        for (UUID id : ids) {
            try {
                verify(id);
            } catch (CatalogueException e) {
                // Provider still unavailable: keep the state and try again on the next run.
            }
        }
    }

    private static UploadResult fromBody(JsonNode body) {
        return new UploadResult(UUID.fromString(body.get("mediaId").asText()), body.get("state").asText(),
                body.get("contentType").asText(), body.get("byteCount").asLong());
    }

    private static Instant now() {
        return CatalogueValues.toStoredInstant(Instant.now());
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
