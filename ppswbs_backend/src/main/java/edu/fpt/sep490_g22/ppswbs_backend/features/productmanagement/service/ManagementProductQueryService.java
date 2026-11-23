package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.VariantBranchStockRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import org.springframework.beans.factory.annotation.Autowired;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos.ManagementProduct;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos.ManagementProductPage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Management list/detail of products (T026, FR-003, A-002).
 *
 * <ul>
 *   <li>Keyword (trimmed, at most 200 code points) matches a case-insensitive name substring, the product
 *       number ({@code 123} or {@code SP000123}) or an exact variant SKU. Every size is returned with its
 *       SKU, so the client can highlight the size whose SKU matched.</li>
 *   <li>20 items per page, newest first by creation time then ID; deleted products are excluded.</li>
 *   <li>Proposed, approved and applied prices, review state and publication are separate fields.</li>
 *   <li>Held/available quantities must be authoritative. A product that was never published cannot have
 *       a hold, so its held quantity is a proven zero; otherwise the FE-08 source is required and its
 *       absence is {@code AVAILABILITY_DEPENDENCY_UNAVAILABLE}, never zero.</li>
 * </ul>
 * Callers authorize the actor before calling (T007).
 */
@Service
public class ManagementProductQueryService {

    private static final Pattern PRODUCT_NUMBER = Pattern.compile("(?:SP)?0*([1-9][0-9]{0,18})");
    private static final Pattern SKU = Pattern.compile("SP[0-9]{6,19}-S(0[5-9]|1[0-9]|2[0-9])");

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductCategoryRepository categories;
    private final ProductImageRepository images;
    private final ProductReviewRepository reviews;
    private final PriceProposalRepository proposals;
    private final ObjectProvider<VariantHoldReader> holdReader;
    private final ObjectProvider<VariantBranchStockRepository> branchStocks;
    private final ObjectProvider<ProductBranchStockService> branchStockService;
    private final ObjectProvider<CatalogueActorNames> actorNames;

    /** Without branch data (unit tests of the base read). */
    public ManagementProductQueryService(ProductRepository products, ProductVariantRepository variants,
                                         ProductCategoryRepository categories, ProductImageRepository images,
                                         ProductReviewRepository reviews, PriceProposalRepository proposals,
                                         ObjectProvider<VariantHoldReader> holdReader) {
        this(products, variants, categories, images, reviews, proposals, holdReader, null, null, null);
    }

    /**
     * Branch data (US10) is optional so this read never depends on the branch source: without names a branch is shown
     * by id. The branch service is resolved lazily because it creates products through this read.
     */
    @Autowired
    public ManagementProductQueryService(ProductRepository products, ProductVariantRepository variants,
                                         ProductCategoryRepository categories, ProductImageRepository images,
                                         ProductReviewRepository reviews, PriceProposalRepository proposals,
                                         ObjectProvider<VariantHoldReader> holdReader,
                                         ObjectProvider<VariantBranchStockRepository> branchStocks,
                                         ObjectProvider<ProductBranchStockService> branchStockService,
                                         ObjectProvider<CatalogueActorNames> actorNames) {
        this.actorNames = actorNames;
        this.branchStocks = branchStocks;
        this.branchStockService = branchStockService;
        this.products = products;
        this.variants = variants;
        this.categories = categories;
        this.images = images;
        this.reviews = reviews;
        this.proposals = proposals;
        this.holdReader = holdReader;
    }

    /** Parsed keyword; {@code anyKeyword} means no keyword filter. */
    record Keyword(boolean anyKeyword, String namePattern, Long productNumber, String sku) {

        static Keyword parse(String raw) {
            String trimmed = CatalogueValues.searchQuery("q", raw);
            if (trimmed == null) {
                return new Keyword(true, null, null, null);
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);
            Long number = null;
            Matcher matcher = PRODUCT_NUMBER.matcher(upper);
            if (matcher.matches()) {
                BigInteger value = new BigInteger(matcher.group(1));
                if (value.bitLength() < 64) {
                    number = value.longValue();
                }
            }
            String sku = SKU.matcher(upper).matches() ? upper : null;
            return new Keyword(false, "%" + escapeLike(trimmed.toLowerCase(Locale.ROOT)) + "%", number, sku);
        }

        private static String escapeLike(String text) {
            return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        }
    }

    @Transactional(readOnly = true)
    public ManagementProductPage list(String q, String categoryId, Boolean published, Integer page) {
        return list(q, categoryId, published, page, null);
    }

    /** FR-064/FR-065 list tabs (Session 2026-11-22); the request value is the enum name. */
    public enum ListTab { DRAFT, PENDING, REJECTED, APPROVED, PUBLISHED, HIDDEN, PRICE_PENDING, PRICE_DRAFT, PRICE_REJECTED }

    /** Repository arguments of one tab; {@link #NONE} = every non-deleted product. */
    record TabFilter(Boolean published, Boolean everPublished, ReviewState reviewState, boolean draftIfNoReview,
                     boolean pricePending, ReviewState priceState) {

        static final TabFilter NONE = new TabFilter(null, null, null, false, false, ReviewState.PENDING);

        static TabFilter of(ListTab tab) {
            return switch (tab) {
                case DRAFT -> new TabFilter(null, false, ReviewState.DRAFT, true, false, ReviewState.PENDING);
                case PENDING -> new TabFilter(null, false, ReviewState.PENDING, false, false, ReviewState.PENDING);
                case REJECTED -> new TabFilter(null, false, ReviewState.REJECTED, false, false, ReviewState.PENDING);
                case APPROVED -> new TabFilter(false, false, ReviewState.APPROVED, false, false, ReviewState.PENDING);
                case PUBLISHED -> new TabFilter(true, null, null, false, false, ReviewState.PENDING);
                case HIDDEN -> new TabFilter(false, true, null, false, false, ReviewState.PENDING);
                case PRICE_PENDING -> new TabFilter(null, true, null, false, true, ReviewState.PENDING);
                // FR-065 sub-filter (Session 2026-11-22): standalone price drafts not sent yet.
                case PRICE_DRAFT -> new TabFilter(null, true, null, false, true, ReviewState.DRAFT);
                // Sub-filter added 2026-11-23: rejected standalone prices, until the size gets a new proposal.
                case PRICE_REJECTED -> new TabFilter(null, true, null, false, true, ReviewState.REJECTED);
            };
        }
    }

    /** The tab of a request; null when absent or blank, a validation error on field {@code tab} when unknown. */
    public static ListTab parseTab(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ListTab.valueOf(raw.trim());
        } catch (IllegalArgumentException unknown) {
            throw CatalogueException.invalidField("tab", "Unknown list tab.");
        }
    }

    /** FR-064: {@code tab} decides the state filter when given ({@code published} is then ignored). */
    @Transactional(readOnly = true)
    public ManagementProductPage list(String q, String categoryId, Boolean published, Integer page, String locationId,
                                      ListTab tab) {
        if (tab == null) {
            return list(q, categoryId, published, page, locationId);
        }
        Keyword keyword = Keyword.parse(q);
        Long category = categoryId == null ? null : CatalogueValues.parseId("categoryId", categoryId);
        Long location = locationId == null ? null : CatalogueValues.parseId("locationId", locationId);
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw CatalogueException.invalidField("page", "Page must be zero or greater.");
        }
        TabFilter filter = TabFilter.of(tab);
        Page<Long> ids = products.searchManagementIdsByTab(location, category, filter.published(),
                filter.everPublished(), filter.reviewState(), filter.draftIfNoReview(), filter.pricePending(),
                filter.priceState(), keyword.anyKeyword(), keyword.namePattern(), keyword.productNumber(),
                keyword.sku(), PageRequest.of(pageNumber, ManagementProductDtos.PAGE_SIZE));
        return new ManagementProductPage(load(ids.getContent()), pageNumber, ManagementProductDtos.PAGE_SIZE,
                Long.toString(ids.getTotalElements()));
    }

    /** FR-064 counts of every tab for the list filters, with the FR-065 pending, draft and rejected price proposal counts. */
    @Transactional(readOnly = true)
    public ManagementProductDtos.TabCounts tabCounts(String q, String categoryId, String locationId) {
        Keyword keyword = Keyword.parse(q);
        Long category = categoryId == null ? null : CatalogueValues.parseId("categoryId", categoryId);
        Long location = locationId == null ? null : CatalogueValues.parseId("locationId", locationId);
        java.util.function.Function<TabFilter, String> count = filter -> Long.toString(products.countManagementByTab(
                location, category, filter.published(), filter.everPublished(), filter.reviewState(),
                filter.draftIfNoReview(), filter.pricePending(), filter.priceState(), keyword.anyKeyword(),
                keyword.namePattern(), keyword.productNumber(), keyword.sku()));
        String pricePending = Long.toString(products.countPriceProposals(
                location, category, null, null, null, false, false, ReviewState.PENDING, keyword.anyKeyword(),
                keyword.namePattern(), keyword.productNumber(), keyword.sku()));
        String priceDraft = Long.toString(products.countPriceProposals(
                location, category, null, null, null, false, false, ReviewState.DRAFT, keyword.anyKeyword(),
                keyword.namePattern(), keyword.productNumber(), keyword.sku()));
        String priceRejected = Long.toString(products.countPriceProposals(
                location, category, null, null, null, false, false, ReviewState.REJECTED, keyword.anyKeyword(),
                keyword.namePattern(), keyword.productNumber(), keyword.sku()));
        return new ManagementProductDtos.TabCounts(count.apply(TabFilter.NONE),
                count.apply(TabFilter.of(ListTab.DRAFT)), count.apply(TabFilter.of(ListTab.PENDING)),
                count.apply(TabFilter.of(ListTab.REJECTED)), count.apply(TabFilter.of(ListTab.APPROVED)),
                count.apply(TabFilter.of(ListTab.PUBLISHED)), count.apply(TabFilter.of(ListTab.HIDDEN)), pricePending,
                priceDraft, priceRejected);
    }

    /** {@code locationId}: only products sold at this branch (US10, FR-051); null = every product. */
    @Transactional(readOnly = true)
    public ManagementProductPage list(String q, String categoryId, Boolean published, Integer page, String locationId) {
        Keyword keyword = Keyword.parse(q);
        Long category = categoryId == null ? null : CatalogueValues.parseId("categoryId", categoryId);
        Long location = locationId == null ? null : CatalogueValues.parseId("locationId", locationId);
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw CatalogueException.invalidField("page", "Page must be zero or greater.");
        }
        PageRequest pageable = PageRequest.of(pageNumber, ManagementProductDtos.PAGE_SIZE);
        Page<Long> ids = location == null
                ? products.searchManagementIds(category, published, keyword.anyKeyword(),
                        keyword.namePattern(), keyword.productNumber(), keyword.sku(), pageable)
                : products.searchManagementIdsAtBranch(location, category, published, keyword.anyKeyword(),
                        keyword.namePattern(), keyword.productNumber(), keyword.sku(), pageable);
        List<ManagementProduct> items = load(ids.getContent());
        return new ManagementProductPage(items, pageNumber, ManagementProductDtos.PAGE_SIZE,
                Long.toString(ids.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public ManagementProduct get(String productId) {
        long id = CatalogueValues.parseId("productId", productId);
        Product product = products.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PRODUCT_NOT_FOUND));
        return load(List.of(product.getId())).get(0);
    }

    /** Batch-loads the products of one page and keeps the page order. */
    private List<ManagementProduct> load(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> productById = byId(products.findAllById(productIds), Product::getId);
        Map<Long, ProductCategory> categoryById = byId(categories.findAllById(
                productById.values().stream().map(Product::getCategoryId).distinct().toList()), ProductCategory::getId);
        Map<Long, List<ProductVariant>> variantsByProduct = variants
                .findByProductIdInOrderByProductIdAscRingSizeAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));
        Map<Long, List<ProductImage>> imagesByProduct = images
                .findByProductIdInOrderByProductIdAscDisplayPositionAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));
        Map<Long, List<ProductReview>> reviewsByProduct = reviews
                .findByProductIdInOrderByProductIdAscRevisionAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductReview::getProductId));
        List<Long> variantIds = variantsByProduct.values().stream().flatMap(List::stream)
                .map(ProductVariant::getId).toList();
        Map<Long, List<PriceProposal>> proposalsByVariant = variantIds.isEmpty() ? Map.of() : proposals
                .findByVariantIdInOrderByVariantIdAscRevisionAscIdAsc(variantIds).stream()
                .collect(Collectors.groupingBy(PriceProposal::getVariantId));
        Map<Long, BigInteger> heldByVariant = heldQuantities(productById.values(), variantsByProduct);
        VariantBranchStockRepository stockRepository = branchStocks == null ? null : branchStocks.getIfAvailable();
        Map<Long, List<VariantBranchStock>> stocksByVariant = stockRepository == null || variantIds.isEmpty() ? Map.of()
                : stockRepository.findByVariantIdInOrderByVariantIdAscLocationIdAsc(variantIds).stream()
                        .collect(Collectors.groupingBy(VariantBranchStock::getVariantId));
        ProductBranchStockService branchService = stocksByVariant.isEmpty() || branchStockService == null ? null
                : branchStockService.getIfAvailable();
        Map<Long, SellingLocation> activeBranches = branchService == null ? Map.of() : branchService.activeBranchesOrEmpty();
        Map<BranchVariant, BigInteger> heldByBranch = branchHeldQuantities(productById.values(), variantsByProduct,
                stocksByVariant);
        Map<String, ManagementProductDtos.ActorRef> actors = actorLabels(reviewsByProduct, proposalsByVariant);

        List<ManagementProduct> result = new ArrayList<>(productIds.size());
        for (Long id : productIds) {
            Product product = productById.get(id);
            if (product == null) {
                continue;
            }
            result.add(toDto(product, categoryById.get(product.getCategoryId()),
                    variantsByProduct.getOrDefault(id, List.of()), imagesByProduct.getOrDefault(id, List.of()),
                    reviewsByProduct.getOrDefault(id, List.of()), proposalsByVariant, heldByVariant,
                    stocksByVariant, activeBranches, heldByBranch, actors));
        }
        return result;
    }

    /** FR-060: labels of every submitter/decider of the loaded rows, one identity lookup per distinct actor. */
    private Map<String, ManagementProductDtos.ActorRef> actorLabels(Map<Long, List<ProductReview>> reviewsByProduct,
                                                                    Map<Long, List<PriceProposal>> proposalsByVariant) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        reviewsByProduct.values().forEach(rows -> rows.forEach(review -> {
            ids.add(review.getSubmittedBy());
            ids.add(review.getDecidedBy());
        }));
        proposalsByVariant.values().forEach(rows -> rows.forEach(proposal -> {
            ids.add(proposal.getSubmittedBy());
            ids.add(proposal.getDecidedBy());
        }));
        ids.remove(null);
        CatalogueActorNames names = actorNames == null ? null : actorNames.getIfAvailable();
        return names == null || ids.isEmpty() ? Map.of() : names.resolve(ids);
    }

    private static ManagementProductDtos.ActorRef actor(Map<String, ManagementProductDtos.ActorRef> actors, String actorId) {
        return actorId == null ? null : actors.getOrDefault(actorId, CatalogueActorNames.fallback(actorId));
    }

    /**
     * FR-051: holds per size × branch of the loaded rows. A never-published product has none (0 at every branch).
     * Empty when the FE-08 source still holds per size: the DTO then gives no per-branch numbers.
     */
    private Map<BranchVariant, BigInteger> branchHeldQuantities(Collection<Product> loaded,
                                                                Map<Long, List<ProductVariant>> variantsByProduct,
                                                                Map<Long, List<VariantBranchStock>> stocksByVariant) {
        if (stocksByVariant.isEmpty()) {
            return Map.of();
        }
        VariantHoldReader reader = holdReader.getIfAvailable();
        List<BranchVariant> needSource = new ArrayList<>();
        List<BranchVariant> neverPublished = new ArrayList<>();
        for (Product product : loaded) {
            for (ProductVariant variant : variantsByProduct.getOrDefault(product.getId(), List.of())) {
                for (VariantBranchStock stock : stocksByVariant.getOrDefault(variant.getId(), List.of())) {
                    BranchVariant key = new BranchVariant(variant.getId(), stock.getLocationId());
                    (product.hasEverBeenPublished() ? needSource : neverPublished).add(key);
                }
            }
        }
        Map<BranchVariant, BigInteger> fromSource = needSource.isEmpty() || reader == null ? Map.of()
                : reader.validHeldByBranch(needSource);
        if (fromSource.isEmpty() && !needSource.isEmpty()) {
            return Map.of(); // not known per branch: keep the size rule everywhere
        }
        Map<BranchVariant, BigInteger> held = new HashMap<>();
        neverPublished.forEach(key -> held.put(key, BigInteger.ZERO));
        for (BranchVariant key : needSource) {
            BigInteger value = fromSource.get(key);
            if (value == null || value.signum() < 0) {
                throw new CatalogueException(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
            }
            held.put(key, value);
        }
        return held;
    }

    private Map<Long, BigInteger> heldQuantities(Collection<Product> loaded,
                                                 Map<Long, List<ProductVariant>> variantsByProduct) {
        Map<Long, BigInteger> held = new HashMap<>();
        List<Long> needSource = new ArrayList<>();
        for (Product product : loaded) {
            for (ProductVariant variant : variantsByProduct.getOrDefault(product.getId(), List.of())) {
                if (product.hasEverBeenPublished()) {
                    needSource.add(variant.getId());
                } else {
                    held.put(variant.getId(), BigInteger.ZERO);
                }
            }
        }
        if (!needSource.isEmpty()) {
            VariantHoldReader reader = holdReader.getIfAvailable();
            if (reader == null) {
                throw new CatalogueException(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
            }
            Map<Long, BigInteger> fromSource = reader.validHeldByVariant(needSource);
            for (Long variantId : needSource) {
                BigInteger value = fromSource.get(variantId);
                if (value == null || value.signum() < 0) {
                    throw new CatalogueException(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
                }
                held.put(variantId, value);
            }
        }
        return held;
    }

    private static ManagementProduct toDto(Product product, ProductCategory category, List<ProductVariant> productVariants,
                                           List<ProductImage> productImages, List<ProductReview> productReviews,
                                           Map<Long, List<PriceProposal>> proposalsByVariant,
                                           Map<Long, BigInteger> heldByVariant,
                                           Map<Long, List<VariantBranchStock>> stocksByVariant,
                                           Map<Long, SellingLocation> activeBranches,
                                           Map<BranchVariant, BigInteger> heldByBranch,
                                           Map<String, ManagementProductDtos.ActorRef> actors) {
        if (category == null) {
            throw new IllegalStateException("Product " + product.getId() + " references a missing category");
        }
        return new ManagementProduct(
                CatalogueValues.format(product.getId()),
                product.getName(),
                product.getDescription(),
                new ManagementProductDtos.Category(CatalogueValues.format(category.getId()), category.getName(),
                        category.getDescription(), category.isActive(), category.getVersion()),
                product.isPublished(),
                product.getFirstPublishedAt(),
                product.getDeletedAt(),
                product.getContentVersion(),
                product.getVersion(),
                productImages.stream()
                        .map(image -> new ManagementProductDtos.ImageReference(image.getMediaObjectId(),
                                image.getAltText(), image.getDisplayPosition()))
                        .toList(),
                productVariants.stream()
                        .map(variant -> toDto(variant, heldByVariant.get(variant.getId()),
                                proposalsByVariant.getOrDefault(variant.getId(), List.of()),
                                stocksByVariant.getOrDefault(variant.getId(), List.of()), heldByBranch, actors))
                        .toList(),
                productReviews.stream()
                        .map(review -> new ManagementProductDtos.Review(CatalogueValues.format(review.getId()),
                                review.getRevision(), review.getContentVersion(), review.getState().name(),
                                review.isEffective(), review.getRejectionReason(),
                                actor(actors, review.getSubmittedBy()), review.getSubmittedAt(),
                                SubmitterBranchSnapshot.read(review.getSubmitterBranches(), review.getSubmittedBy()),
                                actor(actors, review.getDecidedBy()), review.getDecidedAt()))
                        .toList(),
                branchesOf(productVariants, stocksByVariant, activeBranches));
    }

    private static ManagementProductDtos.ManagementVariant toDto(ProductVariant variant, BigInteger held,
                                                                 List<PriceProposal> variantProposals,
                                                                 List<VariantBranchStock> variantStocks,
                                                                 Map<BranchVariant, BigInteger> heldByBranch,
                                                                 Map<String, ManagementProductDtos.ActorRef> actors) {
        BigInteger available = variant.getRemainingUnsold().subtract(held).max(BigInteger.ZERO);
        return new ManagementProductDtos.ManagementVariant(
                CatalogueValues.format(variant.getId()),
                variant.getRingSize(),
                variant.getSku(),
                CatalogueValues.format(variant.getRemainingUnsold()),
                CatalogueValues.format(held),
                CatalogueValues.format(available),
                variant.isSaleEnabled(),
                variant.getAppliedPrice() == null ? null : CatalogueValues.format(variant.getAppliedPrice()),
                variant.getAppliedSequence(),
                variant.getVersion(),
                variantProposals.stream()
                        .map(proposal -> new ManagementProductDtos.Proposal(
                                CatalogueValues.format(proposal.getId()),
                                CatalogueValues.format(proposal.getVariantId()),
                                proposal.getRevision(),
                                proposal.getVersion(),
                                proposal.getState().name(),
                                proposal.isEffective(),
                                CatalogueValues.format(proposal.getProposedAmount()),
                                proposal.getApprovedAmount() == null ? null
                                        : CatalogueValues.format(proposal.getApprovedAmount()),
                                proposal.getBaseAppliedSequence(),
                                proposal.getId().equals(variant.getAppliedProposalId()),
                                proposal.getReason(),
                                actor(actors, proposal.getSubmittedBy()), proposal.getSubmittedAt(),
                                SubmitterBranchSnapshot.read(proposal.getSubmitterBranches(),
                                        proposal.getSubmittedBy()),
                                actor(actors, proposal.getDecidedBy()), proposal.getDecidedAt()))
                        .toList(),
                variantStocks.stream()
                        .map(stock -> branchStockDto(stock,
                                heldByBranch.get(new BranchVariant(variant.getId(), stock.getLocationId()))))
                        .toList());
    }

    /** {@code held} null: not known per branch (FE-08 still holds per size), so neither number is given. */
    private static ManagementProductDtos.BranchStock branchStockDto(VariantBranchStock stock, BigInteger held) {
        return new ManagementProductDtos.BranchStock(CatalogueValues.format(stock.getLocationId()),
                CatalogueValues.format(stock.getRemainingUnsold()), stock.isSaleEnabled(), stock.getVersion(),
                held == null ? null : CatalogueValues.format(held),
                held == null ? null : CatalogueValues.format(stock.getRemainingUnsold().subtract(held).max(BigInteger.ZERO)));
    }

    /** Branches of a product, in branch id order: sold there, its name if still active, and whether any size sells. */
    private static List<ManagementProductDtos.Branch> branchesOf(List<ProductVariant> productVariants,
                                                                 Map<Long, List<VariantBranchStock>> stocksByVariant,
                                                                 Map<Long, SellingLocation> activeBranches) {
        Map<Long, Boolean> saleByBranch = new java.util.TreeMap<>();
        for (ProductVariant variant : productVariants) {
            for (VariantBranchStock stock : stocksByVariant.getOrDefault(variant.getId(), List.of())) {
                saleByBranch.merge(stock.getLocationId(), stock.isSaleEnabled(), Boolean::logicalOr);
            }
        }
        List<ManagementProductDtos.Branch> branches = new ArrayList<>();
        saleByBranch.forEach((locationId, sale) -> {
            SellingLocation location = activeBranches.get(locationId);
            branches.add(new ManagementProductDtos.Branch(CatalogueValues.format(locationId),
                    location == null ? null : location.displayName(), location != null, sale));
        });
        return branches;
    }

    private static <T> Map<Long, T> byId(Collection<T> rows, Function<T, Long> idOf) {
        Map<Long, T> map = new HashMap<>();
        for (T row : rows) {
            map.put(idOf.apply(row), row);
        }
        return map;
    }
}
