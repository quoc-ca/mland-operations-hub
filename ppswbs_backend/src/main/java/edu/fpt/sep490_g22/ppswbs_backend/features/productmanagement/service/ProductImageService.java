package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.ErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Replaces the image list of a product: attach, detach, reorder and alt text in one command
 * (T045, US4, FR-008/011/012/024/026).
 *
 * <ul>
 *   <li>At most 10 images, unique media, positions 0–9, alt text 1–200. A newly attached image must be
 *       READY and uploaded by the acting user; an object being deleted is refused by the registry.</li>
 *   <li>The old rows are removed and flushed before the new ones are inserted, so reordering never hits the
 *       unique (product, position) key. Detaching releases only the product's reference: the media object and
 *       references kept by reviews or FE-08 stay; cleanup decides later.</li>
 *   <li>A published product must keep at least one usable image.</li>
 *   <li>Images are reviewable content: a change bumps {@code contentVersion} and, before the first
 *       publication, withdraws the current review (T032).</li>
 *   <li>No provider call is made here; uploads happen before, through {@link CatalogueMediaService}.</li>
 * </ul>
 * The caller authorizes the actor first (T007).
 */
@Service
public class ProductImageService {

    public static final String ACTION = "product.images";

    private final CatalogueLockCoordinator locks;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final CatalogueMediaRepository media;
    private final MediaReferenceService mediaReferences;
    private final ProductReviewInvalidationService invalidation;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;

    public ProductImageService(CatalogueLockCoordinator locks, ProductRepository products,
                               ProductImageRepository images, CatalogueMediaRepository media,
                               MediaReferenceService mediaReferences, ProductReviewInvalidationService invalidation,
                               CatalogueAuditWriter audit, CatalogueCommandExecutor executor) {
        this.locks = locks;
        this.products = products;
        this.images = images;
        this.media = media;
        this.mediaReferences = mediaReferences;
        this.invalidation = invalidation;
        this.audit = audit;
        this.executor = executor;
    }

    public CatalogueCommandExecutor.Outcome replace(String actorId, UUID commandId, String productId,
                                                    long expectedVersion, List<CreateProductService.ImageInput> requested) {
        long id = CatalogueValues.parseId("productId", productId);
        List<CreateProductService.ImageInput> wanted = CreateProductService.validateImages(
                requested == null ? List.of() : List.copyOf(requested)).stream()
                .map(image -> new CreateProductService.ImageInput(image.mediaId(),
                        CatalogueValues.altText("altText", image.altText()), image.position()))
                .toList();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("productId", id);
        request.put("expectedVersion", expectedVersion);
        request.put("images", wanted);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            var locked = locks.lockProduct(id);
            Product product = locked.product();
            product.requireNotDeleted();
            CatalogueLockCoordinator.requireVersion(product.getVersion(), expectedVersion);
            List<ProductImage> current = images.findByProductIdOrderByDisplayPositionAsc(id);
            if (sameImages(current, wanted)) {
                return result(commandId, product, false);
            }

            Set<UUID> currentIds = current.stream().map(ProductImage::getMediaObjectId).collect(Collectors.toSet());
            Set<UUID> wantedIds = wanted.stream().map(CreateProductService.ImageInput::mediaId).collect(Collectors.toSet());
            List<UUID> added = wantedIds.stream().filter(mediaId -> !currentIds.contains(mediaId)).toList();
            List<UUID> removed = currentIds.stream().filter(mediaId -> !wantedIds.contains(mediaId)).toList();
            if (product.isPublished() && !hasUsableImage(wantedIds)) {
                throw new CatalogueException(CatalogueErrorCode.PUBLICATION_PREREQUISITES_MISSING,
                        List.of(new ErrorResponse.FieldError("images",
                                "A published product must keep at least one usable image.")));
            }

            Instant now = CatalogueValues.toStoredInstant(Instant.now());
            String owner = MediaReferenceService.productRef(id);
            mediaReferences.attachOwnedMedia(actorId, MediaReferenceService.OWNER_MODULE, owner, added, now);
            images.deleteAllInBatch(current);
            images.flush();
            images.saveAllAndFlush(wanted.stream()
                    .map(image -> ProductImage.attach(id, image.mediaId(), image.altText(), image.position()))
                    .toList());
            mediaReferences.release(MediaReferenceService.OWNER_MODULE, owner, removed, now);

            product.markContentChanged();
            invalidation.invalidateBeforeFirstPublication(product, locked.variants(), Map.of());
            Product saved = products.saveAndFlush(product);
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, ACTION, "product", saved.getId().toString(),
                    saved.getVersion(), null, List.of("images"),
                    Map.of("imageCount", wanted.size(), "added", added.size(), "removed", removed.size())));
            return result(commandId, saved, true);
        });
    }

    private boolean hasUsableImage(Set<UUID> mediaIds) {
        return media.findAllById(mediaIds).stream()
                .anyMatch(object -> object.getState() == CatalogueMediaObject.State.READY);
    }

    private static boolean sameImages(List<ProductImage> current, List<CreateProductService.ImageInput> wanted) {
        if (current.size() != wanted.size()) {
            return false;
        }
        Map<Integer, CreateProductService.ImageInput> byPosition = wanted.stream()
                .collect(Collectors.toMap(CreateProductService.ImageInput::position, image -> image));
        return current.stream().allMatch(image -> {
            CreateProductService.ImageInput other = byPosition.get(image.getDisplayPosition());
            return other != null && other.mediaId().equals(image.getMediaObjectId())
                    && other.altText().equals(image.getAltText());
        });
    }

    private static CatalogueCommandExecutor.CommandResult result(UUID commandId, Product product, boolean changed) {
        ActionResult body = new ActionResult(commandId, product.getId().toString(), "product", ACTION,
                product.isPublished() ? "PUBLISHED" : "UNPUBLISHED", product.getVersion(), changed);
        return new CatalogueCommandExecutor.CommandResult(200, "product", product.getId().toString(),
                product.getVersion(), body);
    }
}
