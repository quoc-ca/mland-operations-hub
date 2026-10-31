package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
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

    public ManagementProductQueryService(ProductRepository products, ProductVariantRepository variants,
                                         ProductCategoryRepository categories, ProductImageRepository images,
                                         ProductReviewRepository reviews, PriceProposalRepository proposals,
                                         ObjectProvider<VariantHoldReader> holdReader) {
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
        Keyword keyword = Keyword.parse(q);
        Long category = categoryId == null ? null : CatalogueValues.parseId("categoryId", categoryId);
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw CatalogueException.invalidField("page", "Page must be zero or greater.");
        }
        Page<Long> ids = products.searchManagementIds(category, published, keyword.anyKeyword(),
                keyword.namePattern(), keyword.productNumber(), keyword.sku(),
                PageRequest.of(pageNumber, ManagementProductDtos.PAGE_SIZE));
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

        List<ManagementProduct> result = new ArrayList<>(productIds.size());
        for (Long id : productIds) {
            Product product = productById.get(id);
            if (product == null) {
                continue;
            }
            result.add(toDto(product, categoryById.get(product.getCategoryId()),
                    variantsByProduct.getOrDefault(id, List.of()), imagesByProduct.getOrDefault(id, List.of()),
                    reviewsByProduct.getOrDefault(id, List.of()), proposalsByVariant, heldByVariant));
        }
        return result;
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
                                           Map<Long, BigInteger> heldByVariant) {
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
                                proposalsByVariant.getOrDefault(variant.getId(), List.of())))
                        .toList(),
                productReviews.stream()
                        .map(review -> new ManagementProductDtos.Review(CatalogueValues.format(review.getId()),
                                review.getRevision(), review.getContentVersion(), review.getState().name(),
                                review.isEffective(), review.getRejectionReason()))
                        .toList());
    }

    private static ManagementProductDtos.ManagementVariant toDto(ProductVariant variant, BigInteger held,
                                                                 List<PriceProposal> variantProposals) {
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
                                proposal.getReason()))
                        .toList());
    }

    private static <T> Map<Long, T> byId(Collection<T> rows, Function<T, Long> idOf) {
        Map<Long, T> map = new HashMap<>();
        for (T row : rows) {
            map.put(idOf.apply(row), row);
        }
        return map;
    }
}
