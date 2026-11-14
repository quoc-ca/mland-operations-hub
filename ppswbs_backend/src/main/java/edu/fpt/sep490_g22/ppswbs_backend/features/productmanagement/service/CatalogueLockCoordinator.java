package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Single place that takes catalogue row locks, always in the agreed order (data-model "Transaction and
 * concurrency protocol", FR-026):
 *
 * <ol>
 *   <li>identity owner prefix (taken by the caller before calling this coordinator, when needed);</li>
 *   <li>categories by ascending ID (a category change locks both source and target);</li>
 *   <li>products by ascending ID;</li>
 *   <li>variants by ascending ID;</li>
 *   <li>the FE-08 stable source guard, by ascending variant ID (added with AvailabilityGuardPort, T019).</li>
 * </ol>
 * Locks are pessimistic writes held until commit. No remote provider is called while they are held.
 * A lock timeout or deadlock rolls the transaction back and surfaces as the retryable {@code CATALOGUE_BUSY}.
 * Optimistic versions ({@code product.version}, {@code variant.version}) are checked separately with
 * {@link #requireVersion}.
 */
@Service
@RequiredArgsConstructor
public class CatalogueLockCoordinator {

    private final ProductCategoryRepository categories;
    private final ProductRepository products;
    private final ProductVariantRepository variants;

    public record LockRequest(Set<Long> categoryIds, Set<Long> productIds, Set<Long> variantIds) {
        public LockRequest {
            categoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
            productIds = productIds == null ? Set.of() : Set.copyOf(productIds);
            variantIds = variantIds == null ? Set.of() : Set.copyOf(variantIds);
        }
    }

    /** Locked rows keyed by ID, iterated in ascending ID order. */
    public record Locked(Map<Long, ProductCategory> categories, Map<Long, Product> products,
                         Map<Long, ProductVariant> variants) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Locked lock(LockRequest request) {
        Map<Long, ProductCategory> lockedCategories = lockAll(request.categoryIds(), categories::lockAllByIdIn,
                ProductCategory::getId, CatalogueErrorCode.CATEGORY_NOT_FOUND);
        Map<Long, Product> lockedProducts = lockAll(request.productIds(), products::lockAllByIdIn,
                Product::getId, CatalogueErrorCode.PRODUCT_NOT_FOUND);
        Map<Long, ProductVariant> lockedVariants = lockAll(request.variantIds(), variants::lockAllByIdIn,
                ProductVariant::getId, CatalogueErrorCode.VARIANT_NOT_FOUND);
        return new Locked(lockedCategories, lockedProducts, lockedVariants);
    }

    /** A product locked together with its category and all its sizes. */
    public record ProductLock(ProductCategory category, Product product, List<ProductVariant> variants) {
    }

    /**
     * Locks category → product → all sizes of one product in the standard order. The category is read
     * with a scalar query first; if the product moved to another category in between, the caller gets
     * {@code STALE_VERSION} and reloads. Deleted products are returned as they are; callers decide.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductLock lockProduct(long productId) {
        Long categoryId = products.findCategoryIdById(productId)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PRODUCT_NOT_FOUND));
        Locked locked = lock(new LockRequest(Set.of(categoryId), Set.of(productId), Set.of()));
        Product product = locked.products().get(productId);
        if (!categoryId.equals(product.getCategoryId())) {
            throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
        }
        List<ProductVariant> productVariants = variants.lockAllByProductIdIn(List.of(productId));
        return new ProductLock(locked.categories().get(categoryId), product, productVariants);
    }

    /**
     * Like {@link #lockProduct} for a category change: source and target categories are locked together in
     * ascending ID order before the product. The returned lock carries the source category.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductLock lockProductWithTarget(long productId, long targetCategoryId) {
        Long categoryId = products.findCategoryIdById(productId)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PRODUCT_NOT_FOUND));
        // Source and target may be the same category (no move).
        Locked locked = lock(new LockRequest(new TreeSet<>(List.of(categoryId, targetCategoryId)), Set.of(productId),
                Set.of()));
        Product product = locked.products().get(productId);
        if (!categoryId.equals(product.getCategoryId())) {
            throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
        }
        List<ProductVariant> productVariants = variants.lockAllByProductIdIn(List.of(productId));
        return new ProductLock(locked.categories().get(categoryId), product, productVariants);
    }

    /** Rejects a change prepared from an older displayed version. */
    public static void requireVersion(long currentVersion, long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
        }
    }

    private static <T> Map<Long, T> lockAll(Set<Long> ids, Function<Collection<Long>, List<T>> lockQuery,
                                            Function<T, Long> idOf, CatalogueErrorCode missing) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        SortedSet<Long> ascending = new TreeSet<>(ids);
        Map<Long, T> locked = new LinkedHashMap<>();
        for (T row : lockQuery.apply(List.copyOf(ascending))) {
            locked.put(idOf.apply(row), row);
        }
        if (!locked.keySet().containsAll(ascending)) {
            throw new CatalogueException(missing);
        }
        return locked;
    }
}
