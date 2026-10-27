package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos.ManagementProduct;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Creates an unpublished draft product (T025, US1, FR-004–009/026–028).
 *
 * <p>The product gets a new product number, one variant and SKU per size, a DRAFT review (revision 1),
 * one DRAFT price proposal per size and its image references, all in one transaction with audit and the
 * command receipt. Nothing is approved, applied or published. A size starts sale-enabled but cannot be
 * bought before the product is published. Images are optional for a draft.
 *
 * <p>{@link #createDraft} is the shared core: it validates and persists but opens neither a transaction
 * nor a receipt, so manual creation ({@link #create}) and the Excel import (T098) produce identical data.
 */
@Service
public class CreateProductService {

    public static final String CREATE_ACTION = "product.create";

    public enum Source {
        MANUAL,
        IMPORT
    }

    public record VariantInput(Integer size, String proposedPrice, String quantity) {
    }

    public record ImageInput(UUID mediaId, String altText, Integer position) {
    }

    public record CreateProductCommand(String name, String description, String categoryId,
                                       List<VariantInput> variants, List<ImageInput> images) {
        public CreateProductCommand {
            variants = variants == null ? List.of() : List.copyOf(variants);
            images = images == null ? List.of() : List.copyOf(images);
        }
    }

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductReviewRepository reviews;
    private final PriceProposalRepository proposals;
    private final ProductImageRepository images;
    private final CatalogueLockCoordinator locks;
    private final MediaReferenceService mediaReferences;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;
    private final ManagementProductQueryService query;

    public CreateProductService(ProductRepository products, ProductVariantRepository variants,
                                ProductReviewRepository reviews, PriceProposalRepository proposals,
                                ProductImageRepository images, CatalogueLockCoordinator locks,
                                MediaReferenceService mediaReferences, CatalogueAuditWriter audit,
                                CatalogueCommandExecutor executor, ManagementProductQueryService query) {
        this.products = products;
        this.variants = variants;
        this.reviews = reviews;
        this.proposals = proposals;
        this.images = images;
        this.locks = locks;
        this.mediaReferences = mediaReferences;
        this.audit = audit;
        this.executor = executor;
        this.query = query;
    }

    /**
     * Manual creation command. The caller has authorized {@code actorId} for {@code product.create}.
     * A retry with the same key and payload returns the committed result; nothing partial is ever saved.
     */
    public CatalogueCommandExecutor.Outcome create(String actorId, UUID commandId, CreateProductCommand command) {
        CatalogueCommandExecutor.CommandKey key = new CatalogueCommandExecutor.CommandKey(
                actorId, CREATE_ACTION, commandId, CatalogueCommandExecutor.digest(command));
        return executor.execute(key, () -> {
            long productId = createDraft(actorId, command, Source.MANUAL);
            ManagementProduct created = query.get(Long.toString(productId));
            return new CatalogueCommandExecutor.CommandResult(201, "product", Long.toString(productId),
                    created.version(), created);
        });
    }

    /** Shared core; must run inside the caller's transaction. Returns the new product number. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long createDraft(String actorId, CreateProductCommand command, Source source) {
        Objects.requireNonNull(actorId, "actorId");
        long categoryId = CatalogueValues.parseId("categoryId", command.categoryId());
        List<ValidVariant> validVariants = validateVariants(command.variants());
        List<ImageInput> validImages = validateImages(command.images());
        Product product = Product.draft(command.name(), command.description(), categoryId);

        ProductCategory category = locks.lock(new CatalogueLockCoordinator.LockRequest(
                Set.of(categoryId), Set.of(), Set.of())).categories().get(categoryId);
        if (!category.isActive()) {
            throw new CatalogueException(CatalogueErrorCode.CATEGORY_NOT_FOUND);
        }

        Instant now = CatalogueValues.toStoredInstant(Instant.now());
        Product saved = products.saveAndFlush(product);
        long productId = saved.getId();

        List<ProductVariant> savedVariants = variants.saveAllAndFlush(validVariants.stream()
                .map(input -> ProductVariant.create(productId, input.size(), input.quantity()))
                .toList());

        ProductReview review = reviews.saveAndFlush(ProductReview.draft(productId, 1, saved.getContentVersion()));

        List<PriceProposal> drafts = new ArrayList<>();
        for (int i = 0; i < savedVariants.size(); i++) {
            ProductVariant variant = savedVariants.get(i);
            drafts.add(PriceProposal.draft(variant.getId(), review.getId(), 1, validVariants.get(i).proposedPrice(),
                    variant.getAppliedSequence(), ProposalTerms.digest(variant, review.getId()), null));
        }
        proposals.saveAllAndFlush(drafts);

        if (!validImages.isEmpty()) {
            mediaReferences.attachOwnedMedia(actorId, MediaReferenceService.OWNER_MODULE,
                    MediaReferenceService.productRef(productId),
                    validImages.stream().map(ImageInput::mediaId).toList(), now);
            images.saveAllAndFlush(validImages.stream()
                    .map(image -> ProductImage.attach(productId, image.mediaId(), image.altText(), image.position()))
                    .toList());
        }

        Map<String, Object> safeChanges = new LinkedHashMap<>();
        safeChanges.put("source", source.name());
        safeChanges.put("categoryId", Long.toString(categoryId));
        safeChanges.put("sizes", savedVariants.stream().map(ProductVariant::getRingSize).toList());
        safeChanges.put("imageCount", validImages.size());
        audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, CREATE_ACTION, "product",
                Long.toString(productId), saved.getVersion(), (long) review.getRevision(),
                List.of("name", "description", "categoryId", "variants", "images"), safeChanges));
        return productId;
    }

    private record ValidVariant(int size, BigInteger proposedPrice, BigInteger quantity) {
    }

    private static List<ValidVariant> validateVariants(List<VariantInput> inputs) {
        CatalogueValues.requireSizes("variants", inputs.stream().map(VariantInput::size).toList());
        List<ValidVariant> valid = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            VariantInput input = inputs.get(i);
            String field = "variants[" + i + "]";
            valid.add(new ValidVariant(input.size(),
                    CatalogueValues.parseAmount(field + ".proposedPrice", input.proposedPrice()),
                    CatalogueValues.parseQuantity(field + ".quantity", input.quantity())));
        }
        return valid;
    }

    /** Shared with image replacement (T045): at most 10, unique media and positions 0–9, valid alt text. */
    static List<ImageInput> validateImages(List<ImageInput> inputs) {
        if (inputs.size() > ProductImage.MAX_IMAGES_PER_PRODUCT) {
            throw CatalogueException.invalidField("images", "At most 10 images.");
        }
        Set<UUID> mediaIds = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            ImageInput image = inputs.get(i);
            String field = "images[" + i + "]";
            if (image.mediaId() == null || !mediaIds.add(image.mediaId())) {
                throw CatalogueException.invalidField(field + ".mediaId", "Each image may be used once.");
            }
            if (image.position() == null || image.position() < 0
                    || image.position() >= ProductImage.MAX_IMAGES_PER_PRODUCT || !positions.add(image.position())) {
                throw CatalogueException.invalidField(field + ".position", "Positions must be unique, from 0 to 9.");
            }
            CatalogueValues.altText(field + ".altText", image.altText());
        }
        return inputs;
    }
}
