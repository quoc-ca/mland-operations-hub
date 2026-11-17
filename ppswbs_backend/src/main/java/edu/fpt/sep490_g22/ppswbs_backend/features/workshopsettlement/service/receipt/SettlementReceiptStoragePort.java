package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * 003.3-owned storage of refund receipt images (T154). The only implementation is Cloudinary; the port keeps the
 * service testable without a provider and never exposes a provider URL.
 */
public interface SettlementReceiptStoragePort {

    String storageKeyFor(UUID imageId);

    /** Stores verified bytes under the key; {@code RECEIPT_STORAGE_UNAVAILABLE} when the provider cannot. */
    StoredObject put(String storageKey, String contentType, byte[] bytes);

    /** The bytes of a stored object, or empty when the object is gone. */
    Optional<InputStream> open(StoredObject object);

    /** Deletes the object; an object that is already gone is success. */
    void deleteIfExists(String storageKey);

    record StoredObject(String storageKey, String providerAssetId, String providerVersion, String providerFormat) {
    }
}
