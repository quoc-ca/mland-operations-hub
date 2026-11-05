package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PublicCatalogueQueryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.PublicCatalogueDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Public eligibility and availability of catalogue products (T067, US8, FR-020/031/034/036).
 *
 * <ul>
 *   <li>Eligible: published, not deleted, active category, at least one READY image and an applied price for
 *       every size. Re-checked on every read; an old page grants nothing.</li>
 *   <li>Available = remaining unsold − quantity held by valid FE-08 holds. A size can be bought when it is
 *       sale-enabled and available &gt; 0; the product when at least one size can. Every public product was
 *       published, so the hold source is always required; without it the read fails with
 *       {@code AVAILABILITY_DEPENDENCY_UNAVAILABLE}, never with zero holds.</li>
 *   <li>A disabled size is still listed with its label; a sold-out product stays visible. Only READY images
 *       are listed, as same-origin URLs. Proposals, reviews, quantities and holds are never exposed.</li>
 * </ul>
 */
@Service
public class PublicCatalogueProjection {

    public static final String MEDIA_PATH = "/api/v1/product-media/";

    private final PublicCatalogueQueryRepository publicQueries;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final CatalogueMediaRepository media;
    private final ObjectProvider<VariantHoldReader> holdReader;

    public PublicCatalogueProjection(PublicCatalogueQueryRepository publicQueries, ProductRepository products,
                                     ProductCategoryRepository categories, ProductVariantRepository variants,
                                     ProductImageRepository images, CatalogueMediaRepository media,
                                     ObjectProvider<VariantHoldReader> holdReader) {
        this.publicQueries = publicQueries;
        this.products = products;
        this.categories = categories;
        this.variants = variants;
        this.images = images;
        this.media = media;
        this.holdReader = holdReader;
    }

    /** Whether each size can be bought, for the given sizes of eligible products. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Boolean> canBuy(Collection<ProductVariant> sizes) {
        Map<Long, BigInteger> held = held(sizes.stream().map(ProductVariant::getId).toList());
        return sizes.stream().collect(Collectors.toMap(ProductVariant::getId,
                size -> canBuy(size.isSaleEnabled(), size.getRemainingUnsold(), held.get(size.getId()))));
    }

    static boolean canBuy(boolean saleEnabled, BigInteger remainingUnsold, BigInteger validHeld) {
        return saleEnabled && remainingUnsold.subtract(validHeld).signum() > 0;
    }

    /** Held quantity for every requested size; missing or unverifiable data fails the read. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, BigInteger> held(Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        VariantHoldReader reader = holdReader.getIfAvailable();
        if (reader == null) {
            throw new CatalogueException(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
        }
        Map<Long, BigInteger> held = reader.validHeldByVariant(variantIds);
        for (Long id : variantIds) {
            BigInteger value = held.get(id);
            if (value == null || value.signum() < 0) {
                throw new CatalogueException(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
            }
        }
        return held;
    }

    /**
     * Public views of the given products in the given order; products no longer eligible are left out.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<PublicCatalogueDtos.PublicProduct> load(List<Long> productIds) {
        Set<Long> eligible = new HashSet<>(publicQueries.findEligibleIds(productIds));
        List<Long> ids = productIds.stream().filter(eligible::contains).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> productById = products.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductCategory> categoryById = categories.findAllById(productById.values().stream()
                        .map(Product::getCategoryId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ProductCategory::getId, Function.identity()));
        Map<Long, List<ProductVariant>> sizesByProduct = variants.findByProductIdInOrderByProductIdAscRingSizeAsc(ids)
                .stream().collect(Collectors.groupingBy(ProductVariant::getProductId));
        List<ProductImage> allImages = images.findByProductIdInOrderByProductIdAscDisplayPositionAsc(ids);
        Set<UUID> ready = media.findAllById(allImages.stream().map(ProductImage::getMediaObjectId).toList()).stream()
                .filter(object -> object.getState() == CatalogueMediaObject.State.READY)
                .map(CatalogueMediaObject::getId)
                .collect(Collectors.toSet());
        Map<Long, List<ProductImage>> imagesByProduct = allImages.stream()
                .filter(image -> ready.contains(image.getMediaObjectId()))
                .collect(Collectors.groupingBy(ProductImage::getProductId));
        Map<Long, Boolean> buyable = canBuy(sizesByProduct.values().stream().flatMap(List::stream).toList());

        Map<Long, PublicCatalogueDtos.PublicProduct> views = new LinkedHashMap<>();
        for (Long id : ids) {
            Product product = productById.get(id);
            ProductCategory category = categoryById.get(product.getCategoryId());
            List<ProductVariant> sizes = sizesByProduct.getOrDefault(id, List.of());
            List<PublicCatalogueDtos.PublicVariant> variantViews = sizes.stream()
                    .map(size -> new PublicCatalogueDtos.PublicVariant(size.getId().toString(), size.getRingSize(),
                            size.getSku(), size.getAppliedPrice().toString(), PublicCatalogueDtos.CURRENCY,
                            size.isSaleEnabled(), buyable.get(size.getId())))
                    .toList();
            BigInteger minimum = sizes.stream().map(ProductVariant::getAppliedPrice).min(Comparator.naturalOrder())
                    .orElseThrow();
            BigInteger maximum = sizes.stream().map(ProductVariant::getAppliedPrice).max(Comparator.naturalOrder())
                    .orElseThrow();
            views.put(id, new PublicCatalogueDtos.PublicProduct(id.toString(), product.getName(),
                    product.getDescription(),
                    new PublicCatalogueDtos.PublicCategory(category.getId().toString(), category.getName()),
                    imagesByProduct.getOrDefault(id, List.of()).stream()
                            .map(image -> new PublicCatalogueDtos.PublicImage(image.getId().toString(),
                                    MEDIA_PATH + image.getMediaObjectId(), image.getAltText(), image.getDisplayPosition()))
                            .toList(),
                    variantViews,
                    new PublicCatalogueDtos.PriceRange(minimum.toString(), maximum.toString(), PublicCatalogueDtos.CURRENCY),
                    variantViews.stream().anyMatch(PublicCatalogueDtos.PublicVariant::canBuy),
                    product.getCreatedAt()));
        }
        return List.copyOf(views.values());
    }
}
