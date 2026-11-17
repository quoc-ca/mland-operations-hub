package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueLockCoordinator;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueLockCoordinator.LockRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T024: lock order categories → products → variants, ascending IDs, missing rows rejected.
 * Real lock contention is verified on MySQL in T084.
 */
class CatalogueLockCoordinatorTests {

    private ProductCategoryRepository categories;
    private ProductRepository products;
    private ProductVariantRepository variants;
    private CatalogueLockCoordinator coordinator;

    @BeforeEach
    void setUp() {
        categories = mock(ProductCategoryRepository.class);
        products = mock(ProductRepository.class);
        variants = mock(ProductVariantRepository.class);
        coordinator = new CatalogueLockCoordinator(categories, products, variants);
    }

    @Test
    void locksCategoriesThenProductsThenVariantsInAscendingIds() {
        ProductCategory source = category(9L);
        ProductCategory target = category(4L);
        Product product = product(7L);
        when(categories.lockAllByIdIn(List.of(4L, 9L))).thenReturn(List.of(target, source));
        when(products.lockAllByIdIn(List.of(7L))).thenReturn(List.of(product));
        when(variants.lockAllByIdIn(any())).thenReturn(List.of());

        var locked = coordinator.lock(new LockRequest(Set.of(9L, 4L), Set.of(7L), Set.of()));

        InOrder order = inOrder(categories, products, variants);
        order.verify(categories).lockAllByIdIn(List.of(4L, 9L));
        order.verify(products).lockAllByIdIn(List.of(7L));
        verify(variants, never()).lockAllByIdIn(any());
        assertThat(locked.categories()).containsOnlyKeys(4L, 9L);
        assertThat(locked.products().get(7L)).isSameAs(product);
    }

    @Test
    void missingRowIsNotFound() {
        when(products.lockAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(product(1L)));

        assertThatThrownBy(() -> coordinator.lock(new LockRequest(Set.of(), Set.of(2L, 1L), Set.of())))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    void olderVersionIsStale() {
        CatalogueLockCoordinator.requireVersion(3, 3);
        assertThatThrownBy(() -> CatalogueLockCoordinator.requireVersion(4, 3))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.STALE_VERSION));
    }

    private static ProductCategory category(long id) {
        ProductCategory category = ProductCategory.create("Danh mục " + id, null);
        setId(category, id);
        return category;
    }

    private static Product product(long id) {
        Product product = Product.draft("Nhẫn " + id, "Mô tả", 1L);
        setId(product, id);
        return product;
    }

    private static void setId(Object entity, long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
