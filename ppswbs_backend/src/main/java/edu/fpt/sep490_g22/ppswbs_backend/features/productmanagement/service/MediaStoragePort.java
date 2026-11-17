package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Provider-neutral image storage boundary of productmanagement (interfaces "MediaStoragePort", research D-06).
 * Implemented by the Cloudinary adapter; replaceable by a test double. Never called while a database
 * lock is held.
 *
 * <p>Errors: a temporary provider failure throws {@code MEDIA_DEPENDENCY_UNAVAILABLE}; reusing a storage
 * key for different content throws {@code IDEMPOTENCY_KEY_REUSED}. "Missing" is a normal result, distinct
 * from "unavailable".
 */
public interface MediaStoragePort {

    /** Already verified image bytes (type, signature and size checked by the media service). */
    record VerifiedContent(byte[] bytes, String contentType, String sha256Hex) {
        public VerifiedContent {
            Objects.requireNonNull(bytes, "bytes");
            Objects.requireNonNull(contentType, "contentType");
            Objects.requireNonNull(sha256Hex, "sha256Hex");
        }
    }

    /** Provider metadata needed to read, verify and delete exactly this object. */
    record StoredObject(String storageKey, String providerAssetId, String providerVersion, String providerFormat) {
        public StoredObject {
            Objects.requireNonNull(storageKey, "storageKey");
            Objects.requireNonNull(providerAssetId, "providerAssetId");
            Objects.requireNonNull(providerVersion, "providerVersion");
            Objects.requireNonNull(providerFormat, "providerFormat");
        }
    }

    enum Availability {
        READABLE,
        MISSING
    }

    /** Deterministic storage key for a staged media object; retries of the same command reuse it. */
    String storageKeyFor(UUID mediaId);

    /**
     * Stores the content under the key unless it already exists. A retry with the same content returns
     * the existing object; different content under the same key is a conflict, never an overwrite.
     */
    StoredObject putIfAbsent(String storageKey, VerifiedContent content);

    Availability verifyReadable(StoredObject object);

    /** Opens the stored bytes for the application to serve; empty when the object no longer exists. */
    Optional<InputStream> open(StoredObject object);

    /** Idempotent: an already missing object counts as deleted. */
    void deleteIfExists(String storageKey);
}
