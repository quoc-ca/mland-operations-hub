package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.ErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Explicit publication and hiding (T038, FR-017/018/020).
 *
 * <p>Publishing requires: not deleted, active category, at least one usable image, an applied approved
 * price for every size and, before the first publication, an effective approved initial review for the
 * current content. A size with quantity 0 does not block publication. {@code firstPublishedAt} is set once.
 * Hiding keeps the product in management and does not touch accepted holds or orders. Asking for the
 * state the product already has is a no-op ({@code changed=false}) and does not need a matching version.
 * The caller authorizes the actor first (T007).
 */
@Service
public class ProductPublicationService {

    public static final String PUBLISH_ACTION = "product.publish";
    public static final String UNPUBLISH_ACTION = "product.unpublish";

    private final CatalogueLockCoordinator locks;
    private final ProductReviewRepository reviews;
    private final PriceProposalRepository proposals;
    private final ProductImageRepository images;
    private final CatalogueMediaRepository media;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;

    public ProductPublicationService(CatalogueLockCoordinator locks, ProductReviewRepository reviews,
                                     PriceProposalRepository proposals, ProductImageRepository images,
                                     CatalogueMediaRepository media, CatalogueAuditWriter audit,
                                     CatalogueCommandExecutor executor) {
        this.locks = locks;
        this.reviews = reviews;
        this.proposals = proposals;
        this.images = images;
        this.media = media;
        this.audit = audit;
        this.executor = executor;
    }

    public CatalogueCommandExecutor.Outcome setPublication(String actorId, UUID commandId, String productId,
                                                           long expectedVersion, boolean published) {
        long id = CatalogueValues.parseId("productId", productId);
        String action = published ? PUBLISH_ACTION : UNPUBLISH_ACTION;
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("productId", id);
        request.put("expectedVersion", expectedVersion);
        request.put("published", published);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, action, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            var locked = locks.lockProduct(id);
            Product product = locked.product();
            product.requireNotDeleted();
            if (product.isPublished() == published) {
                return result(commandId, product, action, false);
            }
            CatalogueLockCoordinator.requireVersion(product.getVersion(), expectedVersion);
            Instant now = CatalogueValues.toStoredInstant(Instant.now());
            if (published) {
                List<ErrorResponse.FieldError> missing = missingPrerequisites(locked);
                if (!missing.isEmpty()) {
                    throw new CatalogueException(CatalogueErrorCode.PUBLICATION_PREREQUISITES_MISSING, missing);
                }
                product.publish(now);
            } else {
                product.unpublish();
            }
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, action, "product",
                    Long.toString(product.getId()), product.getVersion(), null, List.of("published"),
                    Map.of("published", published)));
            return result(commandId, product, action, true);
        });
    }

    /** Every unmet condition, so the user sees all of them at once (US3.5). */
    List<ErrorResponse.FieldError> missingPrerequisites(CatalogueLockCoordinator.ProductLock locked) {
        Product product = locked.product();
        List<ErrorResponse.FieldError> missing = new ArrayList<>();
        if (!locked.category().isActive()) {
            missing.add(new ErrorResponse.FieldError("categoryId", "The category must be active."));
        }
        List<ProductImage> productImages = images.findByProductIdOrderByDisplayPositionAsc(product.getId());
        Map<UUID, CatalogueMediaObject> mediaById = media.findAllById(
                        productImages.stream().map(ProductImage::getMediaObjectId).toList()).stream()
                .collect(Collectors.toMap(CatalogueMediaObject::getId, Function.identity()));
        boolean usableImage = productImages.stream().anyMatch(image -> {
            CatalogueMediaObject object = mediaById.get(image.getMediaObjectId());
            return object != null && object.getState() == CatalogueMediaObject.State.READY;
        });
        if (!usableImage) {
            missing.add(new ErrorResponse.FieldError("images", "At least one usable image is required."));
        }

        ProductReview initialReview = null;
        if (!product.hasEverBeenPublished()) {
            initialReview = reviews.findFirstByProductIdOrderByRevisionDesc(product.getId())
                    .filter(review -> review.isEffectiveApproval()
                            && review.getContentVersion() == product.getContentVersion())
                    .orElse(null);
            if (initialReview == null) {
                missing.add(new ErrorResponse.FieldError("review", "The current content must be approved."));
            }
        }
        Map<Long, PriceProposal> appliedProposals = proposals.findAllById(locked.variants().stream()
                        .map(ProductVariant::getAppliedProposalId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(PriceProposal::getId, Function.identity()));
        Long initialReviewId = initialReview == null ? null : initialReview.getId();
        for (ProductVariant variant : locked.variants()) {
            PriceProposal applied = variant.getAppliedProposalId() == null ? null
                    : appliedProposals.get(variant.getAppliedProposalId());
            boolean valid = variant.hasAppliedPrice() && applied != null && applied.getState() == ReviewState.APPROVED
                    && (product.hasEverBeenPublished() || Objects.equals(applied.getReviewId(), initialReviewId));
            if (!valid) {
                missing.add(new ErrorResponse.FieldError("variants[" + variant.getSku() + "].appliedPrice",
                        "Every size needs an applied approved price."));
            }
        }
        return missing;
    }

    private static CatalogueCommandExecutor.CommandResult result(UUID commandId, Product product, String action,
                                                                 boolean changed) {
        ActionResult body = new ActionResult(commandId, product.getId().toString(), "product", action,
                product.isPublished() ? "PUBLISHED" : "UNPUBLISHED", null, changed);
        return new CatalogueCommandExecutor.CommandResult(200, "product", product.getId().toString(), null, body);
    }
}
