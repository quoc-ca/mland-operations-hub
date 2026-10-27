package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PublicCatalogueQueryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Serves the bytes of a public product image (T070, interfaces "MediaStoragePort", FR-031/036).
 *
 * <ul>
 *   <li>Checked on every request from the database: the image must be READY and attached to at least one
 *       product that is publicly eligible right now. Otherwise it is {@code MEDIA_NOT_FOUND} (404), exactly like
 *       an image that never existed, so hidden or deleted products do not leak.</li>
 *   <li>The bytes are streamed through the application from a signed URL used only inside the backend; the
 *       provider URL is never returned or redirected to, and the response is {@code no-store}, so unpublishing or
 *       deleting does not depend on CDN invalidation.</li>
 *   <li>If the provider has lost the object, it is recorded UNAVAILABLE through {@link CatalogueMediaService#verify}
 *       (its products leave the public catalogue) and the answer is 404. A provider failure is
 *       {@code MEDIA_DEPENDENCY_UNAVAILABLE} (503). The database check runs in a short
 *       read transaction that ends before the provider call.</li>
 * </ul>
 */
@Service
public class PublicMediaService {

    public record PublicMedia(String contentType, InputStream content) {
    }

    private record Located(String contentType, MediaStoragePort.StoredObject stored) {
    }

    private final CatalogueMediaRepository media;
    private final ProductImageRepository images;
    private final PublicCatalogueQueryRepository publicQueries;
    private final MediaStoragePort storage;
    private final TransactionTemplate transaction;
    private final CatalogueMediaService mediaVerification;

    public PublicMediaService(CatalogueMediaRepository media, ProductImageRepository images,
                              PublicCatalogueQueryRepository publicQueries, MediaStoragePort storage,
                              TransactionTemplate transaction, CatalogueMediaService mediaVerification) {
        this.media = media;
        this.images = images;
        this.publicQueries = publicQueries;
        this.storage = storage;
        this.transaction = transaction;
        this.mediaVerification = mediaVerification;
    }

    public PublicMedia open(String rawMediaId) {
        UUID mediaId = parse(rawMediaId);
        Located located = transaction.execute(status -> {
            CatalogueMediaObject object = media.findById(mediaId)
                    .filter(found -> found.getState() == CatalogueMediaObject.State.READY)
                    .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND));
            List<Long> products = images.findProductIdsByMediaObjectId(mediaId);
            if (publicQueries.findEligibleIds(products).isEmpty()) {
                throw new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND);
            }
            return new Located(object.getContentType(), new MediaStoragePort.StoredObject(object.getStorageKey(),
                    object.getProviderAssetId(), object.getProviderVersion(), object.getProviderFormat()));
        });
        Optional<InputStream> content = storage.open(located.stored());
        if (content.isEmpty()) {
            // The delivery URL found nothing: record it through the authoritative check (T073), then 404.
            mediaVerification.verify(mediaId);
            throw new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND);
        }
        return new PublicMedia(located.contentType(), content.get());
    }

    private static UUID parse(String raw) {
        try {
            UUID id = UUID.fromString(raw);
            if (!id.toString().equals(raw)) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (RuntimeException e) {
            throw new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND);
        }
    }
}
