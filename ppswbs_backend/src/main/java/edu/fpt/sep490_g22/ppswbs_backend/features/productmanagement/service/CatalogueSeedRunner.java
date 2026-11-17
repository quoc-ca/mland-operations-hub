package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sample catalogue for every developer database (2026-11-13): categories and published ring products with real
 * photos, read from {@code classpath:catalogue-seed/catalogue-seed.json}. Runs once in the background after start-up
 * and goes through the same services as the management screens (create with branches → submit → approve → apply
 * every price → publish), so data, SKUs, audit and command receipts are the ordinary ones.
 *
 * <ul>
 *   <li>Idempotent: a product already present (same name in the same category, not deleted) is skipped; every
 *       command has a key derived from the product name, so a run cut off half way resumes without duplicates.</li>
 *   <li>Images go to the media storage configured on that machine (Cloudinary). Without it the run stops with a
 *       warning and creates nothing, because a product cannot be published without an image (FR-017).</li>
 *   <li>Branches are found by their 003.2 code; a missing code is left out, no other branch is guessed.</li>
 *   <li>Off with {@code app.catalogue.seed.enabled=false}; never runs in tests (JUnit on the class path).</li>
 * </ul>
 * The actor is an internal one ({@value #ACTOR}): without an HTTP actor the branch scope is unrestricted, as for
 * any internal call (see {@link CatalogueBranchScope#current()}).
 */
@Component
public class CatalogueSeedRunner {

    static final String ACTOR = "system:catalogue-seed";
    private static final String RESOURCE = "catalogue-seed/catalogue-seed.json";
    private static final Logger log = LoggerFactory.getLogger(CatalogueSeedRunner.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Seed(List<SeedCategory> categories, List<SeedProduct> products) {
    }

    record SeedCategory(String name, String description) {
    }

    record SeedImage(String file, String alt) {
    }

    record SeedProduct(String name, String category, String sizes, String price, Map<String, Integer> quantities,
                       String description, List<SeedImage> images) {
    }

    private final boolean enabled;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ProductCategoryRepository categories;
    private final ProductRepository products;
    private final ProductReviewRepository reviews;
    private final PriceProposalRepository proposals;
    private final ProductCategoryService categoryService;
    private final CatalogueMediaService media;
    private final CreateProductService createProducts;
    private final ProductReviewService reviewService;
    private final PriceApplicationService applyService;
    private final ProductPublicationService publication;
    private final ManagementProductQueryService query;

    public CatalogueSeedRunner(@Value("${app.catalogue.seed.enabled:true}") boolean enabled, ObjectMapper json,
                               JdbcTemplate jdbc, TransactionTemplate transaction,
                               ProductCategoryRepository categories, ProductRepository products,
                               ProductReviewRepository reviews, PriceProposalRepository proposals,
                               ProductCategoryService categoryService, CatalogueMediaService media,
                               CreateProductService createProducts, ProductReviewService reviewService,
                               PriceApplicationService applyService, ProductPublicationService publication,
                               ManagementProductQueryService query) {
        this.enabled = enabled;
        this.json = json;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.categories = categories;
        this.products = products;
        this.reviews = reviews;
        this.proposals = proposals;
        this.categoryService = categoryService;
        this.media = media;
        this.createProducts = createProducts;
        this.reviewService = reviewService;
        this.applyService = applyService;
        this.publication = publication;
        this.query = query;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled || ClassUtils.isPresent("org.junit.jupiter.api.Test", getClass().getClassLoader())) {
            return;
        }
        Thread worker = new Thread(this::runSafely, "catalogue-seed");
        worker.setDaemon(true);
        worker.start();
    }

    private void runSafely() {
        try {
            int created = run();
            if (created > 0) {
                log.info("Catalogue seed: {} sample product(s) created and published.", created);
            }
        } catch (MediaUnavailable e) {
            log.warn("Catalogue seed skipped: product images cannot be uploaded ({}). Configure Cloudinary "
                    + "(CLOUDINARY_*) and restart, or set app.catalogue.seed.enabled=false.", e.getMessage());
        } catch (RuntimeException | IOException e) {
            log.warn("Catalogue seed stopped: {}", e.toString());
        }
    }

    /** Returns how many products this run created. */
    int run() throws IOException {
        Seed seed;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            seed = json.readValue(in, Seed.class);
        }
        Map<String, Long> branchIds = branchIds(seed);
        if (branchIds.isEmpty()) {
            log.warn("Catalogue seed skipped: none of the branch codes of {} exists.", RESOURCE);
            return 0;
        }
        Map<String, String> categoryIds = new LinkedHashMap<>();
        for (SeedCategory category : seed.categories()) {
            categoryId(category).ifPresent(id -> categoryIds.put(category.name(), id));
        }
        int created = 0;
        for (SeedProduct product : seed.products()) {
            String categoryId = categoryIds.get(product.category());
            if (categoryId == null || exists(product.name(), categoryId)) {
                continue;
            }
            publish(product, categoryId, branchIds);
            created++;
        }
        return created;
    }

    private Map<String, Long> branchIds(Seed seed) {
        Map<String, Long> ids = new LinkedHashMap<>();
        seed.products().stream().flatMap(product -> product.quantities().keySet().stream()).distinct()
                .forEach(code -> jdbc.query("SELECT id FROM workshop_locations WHERE code = ? AND active = 1",
                        row -> { ids.put(code, row.getLong(1)); }, code));
        return ids;
    }

    /** An existing category with that name (also inactive: then its products are left out), or a new one. */
    private Optional<String> categoryId(SeedCategory seed) {
        byte[] key = ProductCategory.create(seed.name(), seed.description()).getNameKey();
        Optional<ProductCategory> existing = transaction.execute(status -> categories.findByNameKey(key));
        if (existing.isPresent()) {
            return existing.get().isActive() ? Optional.of(existing.get().getId().toString()) : Optional.empty();
        }
        return Optional.of(categoryService.create(ACTOR, key("category", seed.name()), seed.name(),
                seed.description()).resourceId());
    }

    private boolean exists(String name, String categoryId) {
        ManagementProductDtos.ManagementProductPage page = query.list(name, categoryId, null, 0);
        return page.items().stream().anyMatch(item -> item.name().equals(name));
    }

    private void publish(SeedProduct seed, String categoryId, Map<String, Long> branchIds) throws IOException {
        List<CreateProductService.ImageInput> images = new ArrayList<>();
        for (int i = 0; i < seed.images().size(); i++) {
            SeedImage image = seed.images().get(i);
            images.add(new CreateProductService.ImageInput(upload(seed.name(), image), image.alt(), i));
        }
        List<Integer> sizes = sizes(seed.sizes());
        List<CreateProductService.VariantInput> variants = sizes.stream()
                .map(size -> new CreateProductService.VariantInput(size, seed.price(), "0")).toList();
        List<ProductBranchStockService.BranchInput> branches = new ArrayList<>();
        seed.quantities().forEach((code, quantity) -> {
            Long locationId = branchIds.get(code);
            if (locationId != null) {
                branches.add(new ProductBranchStockService.BranchInput(locationId.toString(), sizes.stream()
                        .map(size -> new ProductBranchStockService.SizeQuantity(size, Integer.toString(quantity)))
                        .toList()));
            }
        });
        String productId = createProducts.create(ACTOR, key("create", seed.name()),
                new CreateProductService.CreateProductCommand(seed.name(), seed.description(), categoryId, variants,
                        images, branches)).resourceId();
        long id = Long.parseLong(productId);

        reviewService.submit(ACTOR, key("submit", seed.name()), productId, version(id));
        ProductReview review = currentReview(id);
        List<ProductReviewService.DecisionPrice> prices = proposalsOf(review.getId()).stream()
                .filter(PriceProposal::isCurrentPending)
                .map(p -> new ProductReviewService.DecisionPrice(p.getVariantId().toString(), p.getId().toString(),
                        p.getProposedAmount().toString()))
                .toList();
        reviewService.decide(ACTOR, key("approve", seed.name()), productId, new ProductReviewService.DecisionCommand(
                review.getId().toString(), review.getRevision(), review.getContentVersion(),
                ProductReviewService.Decision.APPROVE, null, prices));
        for (PriceProposal proposal : proposalsOf(currentReview(id).getId())) {
            applyService.apply(ACTOR, key("apply-" + proposal.getId(), seed.name()), proposal.getId().toString(),
                    proposal.getVersion());
        }
        publication.setPublication(ACTOR, key("publish", seed.name()), productId, version(id), true);
    }

    private UUID upload(String productName, SeedImage image) throws IOException {
        byte[] bytes;
        try (InputStream in = new ClassPathResource("catalogue-seed/images/" + image.file()).getInputStream()) {
            bytes = in.readAllBytes();
        }
        try {
            return media.upload(ACTOR, key("image-" + image.file(), productName), "image/jpeg", bytes).mediaId();
        } catch (CatalogueException e) {
            if (e.code() == CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE) {
                throw new MediaUnavailable(e.code().name());
            }
            throw e;
        }
    }

    /** "6-13" → 6..13; "12" → 12. */
    static List<Integer> sizes(String range) {
        String[] parts = range.trim().split("-");
        int low = Integer.parseInt(parts[0].trim());
        int high = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : low;
        List<Integer> sizes = new ArrayList<>();
        for (int size = low; size <= high; size++) {
            sizes.add(size);
        }
        return sizes;
    }

    /** Same step of the same product → same command key, so a retried run replays instead of creating again. */
    static UUID key(String step, String productName) {
        return UUID.nameUUIDFromBytes(("catalogue-seed:" + step + ":" + productName).getBytes(StandardCharsets.UTF_8));
    }

    private long version(long productId) {
        return transaction.execute(status -> products.findById(productId).orElseThrow().getVersion());
    }

    private ProductReview currentReview(long productId) {
        return transaction.execute(status -> reviews.findFirstByProductIdOrderByRevisionDesc(productId).orElseThrow());
    }

    private List<PriceProposal> proposalsOf(long reviewId) {
        return transaction.execute(status -> proposals.findByReviewIdOrderByIdAsc(reviewId));
    }

    private static final class MediaUnavailable extends RuntimeException {
        MediaUnavailable(String message) {
            super(message);
        }
    }
}
