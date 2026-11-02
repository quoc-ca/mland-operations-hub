package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaReferenceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductReviewService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds catalogue data through the real services on the MySQL test database (no provider calls: a READY
 * media row stands in for an uploaded image). Every name is unique so reruns never collide.
 */
@Component
public class CatalogueFlowFixture {

    private final TransactionTemplate transaction;
    private final ProductCategoryRepository categories;
    private final ProductRepository products;
    private final ProductReviewRepository reviews;
    private final PriceProposalRepository proposals;
    private final CatalogueMediaRepository media;
    private final ProductImageRepository images;
    private final MediaReferenceService mediaReferences;
    private final CreateProductService createProducts;
    private final ProductReviewService reviewService;
    private final PriceApplicationService applyService;
    private final ProductPublicationService publication;

    public CatalogueFlowFixture(TransactionTemplate transaction, ProductCategoryRepository categories,
                                ProductRepository products, ProductReviewRepository reviews,
                                PriceProposalRepository proposals, CatalogueMediaRepository media,
                                ProductImageRepository images, MediaReferenceService mediaReferences,
                                CreateProductService createProducts, ProductReviewService reviewService,
                                PriceApplicationService applyService, ProductPublicationService publication) {
        this.transaction = transaction;
        this.categories = categories;
        this.products = products;
        this.reviews = reviews;
        this.proposals = proposals;
        this.media = media;
        this.images = images;
        this.mediaReferences = mediaReferences;
        this.createProducts = createProducts;
        this.reviewService = reviewService;
        this.applyService = applyService;
        this.publication = publication;
    }

    public long activeCategory() {
        return transaction.execute(status ->
                categories.saveAndFlush(ProductCategory.create("Danh mục " + UUID.randomUUID(), null)).getId());
    }

    /** Draft product with sizes 5 (quantity 3) and 12 (quantity 0), no image yet. */
    public long draftProduct(String actorId) {
        var command = new CreateProductService.CreateProductCommand("Nhẫn " + UUID.randomUUID(), "Mô tả",
                Long.toString(activeCategory()), List.of(new CreateProductService.VariantInput(5, "150000", "3"),
                new CreateProductService.VariantInput(12, "180000", "0")), List.of());
        return Long.parseLong(createProducts.create(actorId, UUID.randomUUID(), command).resourceId());
    }

    /** A READY media object uploaded by the actor at the given time, not attached to anything. */
    public UUID readyMedia(String actorId, Instant createdAt) {
        return transaction.execute(status -> {
            UUID id = UUID.randomUUID();
            CatalogueMediaObject object = CatalogueMediaObject.stage(id, "test/" + id, "d".repeat(64), "image/png",
                    1000, actorId, UUID.randomUUID(), createdAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            object.markReady("asset-" + id, "1", "png", createdAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            return media.saveAndFlush(object).getId();
        });
    }

    public CatalogueMediaObject media(UUID mediaId) {
        return transaction.execute(status -> media.findById(mediaId).orElseThrow());
    }

    public List<ProductImage> images(long productId) {
        return transaction.execute(status -> images.findByProductIdOrderByDisplayPositionAsc(productId));
    }

    /** Attaches one READY image (as if uploaded by the actor) at position 0. */
    public void addReadyImage(long productId, String actorId) {
        transaction.executeWithoutResult(status -> {
            UUID id = UUID.randomUUID();
            CatalogueMediaObject object = CatalogueMediaObject.stage(id, "test/" + id, "c".repeat(64), "image/webp",
                    1000, actorId, UUID.randomUUID(), Instant.now());
            object.markReady("asset-" + id, "1", "webp", Instant.now());
            media.saveAndFlush(object);
            mediaReferences.attachOwnedMedia(actorId, MediaReferenceService.OWNER_MODULE,
                    MediaReferenceService.productRef(productId), List.of(id), Instant.now());
            images.saveAndFlush(ProductImage.attach(productId, id, "Mặt trước", 0));
        });
    }

    public long productVersion(long productId) {
        return transaction.execute(status -> products.findById(productId).orElseThrow().getVersion());
    }

    public ProductReview currentReview(long productId) {
        return transaction.execute(status -> reviews.findFirstByProductIdOrderByRevisionDesc(productId).orElseThrow());
    }

    public List<PriceProposal> proposalsOf(long reviewId) {
        return transaction.execute(status -> proposals.findByReviewIdOrderByIdAsc(reviewId));
    }

    public PriceProposal proposal(long proposalId) {
        return transaction.execute(status -> proposals.findById(proposalId).orElseThrow());
    }

    public void submit(long productId, String actorId) {
        reviewService.submit(actorId, UUID.randomUUID(), Long.toString(productId), productVersion(productId));
    }

    /** Approves every size of the current pending review at its proposed amount. */
    public void approveAll(long productId, String managerId) {
        ProductReview review = currentReview(productId);
        List<ProductReviewService.DecisionPrice> prices = proposalsOf(review.getId()).stream()
                .filter(PriceProposal::isCurrentPending)
                .map(proposal -> new ProductReviewService.DecisionPrice(proposal.getVariantId().toString(),
                        proposal.getId().toString(), proposal.getProposedAmount().toString()))
                .toList();
        reviewService.decide(managerId, UUID.randomUUID(), Long.toString(productId),
                new ProductReviewService.DecisionCommand(review.getId().toString(), review.getRevision(),
                        review.getContentVersion(), ProductReviewService.Decision.APPROVE, null, prices));
    }

    /** Draft → image → submitted → approved → every price applied → published (sizes 5 and 12). */
    public long publishedProduct(String staffId, String managerId) {
        long productId = draftProduct(staffId);
        addReadyImage(productId, staffId);
        submit(productId, staffId);
        approveAll(productId, managerId);
        for (PriceProposal proposal : proposalsOf(currentReview(productId).getId())) {
            applyService.apply(staffId, UUID.randomUUID(), proposal.getId().toString(), proposal.getVersion());
        }
        publication.setPublication(staffId, UUID.randomUUID(), Long.toString(productId), productVersion(productId), true);
        return productId;
    }

    public ProductCategory category(long categoryId) {
        return transaction.execute(status -> categories.findById(categoryId).orElseThrow());
    }

    public long categoryOf(long productId) {
        return transaction.execute(status -> products.findById(productId).orElseThrow().getCategoryId());
    }
}
