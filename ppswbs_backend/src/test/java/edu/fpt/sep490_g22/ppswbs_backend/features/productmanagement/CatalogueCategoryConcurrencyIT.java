package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueLockCoordinator;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductCategoryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.UpdateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.Concurrently;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.RealRetailHolds;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T080 on MySQL with real concurrent transactions: duplicate names created at the same time, the category
 * lock serializing activation with publication, opposite category moves without deadlock, and reactivation
 * not reviving deleted/hidden products (FR-026/029/030), and deactivation racing a new REAL FE-08 hold
 * ({@link RealRetailHolds}, develop b0e7796).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import({CatalogueFlowFixture.class, RealRetailHolds.class})
class CatalogueCategoryConcurrencyIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ProductCategoryService categories;
    @Autowired private ProductPublicationService publication;
    @Autowired private UpdateProductService updates;
    @Autowired private DeleteProductService deletion;
    @Autowired private CatalogueLockCoordinator locks;
    @Autowired private ProductCategoryRepository categoryRepository;
    @Autowired private ProductRepository products;
    @Autowired private TransactionTemplate transaction;
    @Autowired private RealRetailHolds retail;
    @Autowired private ProductVariantRepository variants;

    @Test
    void namesDifferingOnlyByCaseCreatedAtTheSameTimeLeaveExactlyOneCategory() {
        for (int round = 0; round < 3; round++) {
            String name = "Đồng thời " + UUID.randomUUID();
            List<Concurrently.Outcome<String>> outcomes = Concurrently.run(
                    () -> categories.create(STAFF, UUID.randomUUID(), name, null).resourceId(),
                    () -> categories.create(MANAGER, UUID.randomUUID(), name.toUpperCase(), null).resourceId());

            assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome ->
                    assertThat(outcome.failure()).isInstanceOfSatisfying(CatalogueException.class,
                            error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.DUPLICATE_CATEGORY_NAME)));
        }
    }

    @Test
    void publicationWaitsForADeactivationHoldingTheCategoryLockAndThenSeesIt() throws Exception {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                false);
        long categoryId = fixture.categoryOf(productId);
        long version = fixture.productVersion(productId);
        CountDownLatch locked = new CountDownLatch(1);

        List<Concurrently.Outcome<Long>> outcomes = Concurrently.run(
                () -> {
                    transaction.executeWithoutResult(status -> {
                        ProductCategory category = locks.lock(new CatalogueLockCoordinator.LockRequest(
                                Set.of(categoryId), Set.of(), Set.of())).categories().get(categoryId);
                        category.setActive(false);
                        locked.countDown();
                        sleep(700);
                    });
                    return 0L;
                },
                () -> {
                    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                    long started = System.nanoTime();
                    try {
                        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId), version, true);
                    } catch (CatalogueException e) {
                        assertThat(e.code()).isEqualTo(CatalogueErrorCode.PUBLICATION_PREREQUISITES_MISSING);
                        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                    }
                    throw new AssertionError("Publication must not succeed in an inactive category");
                });

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        assertThat(outcomes.get(1).value()).isGreaterThanOrEqualTo(400L);
        assertThat(product(productId).isPublished()).isFalse();
    }

    @Test
    void oppositeCategoryMovesAtTheSameTimeDoNotDeadlock() {
        long first = fixture.draftProduct(STAFF);
        long second = fixture.draftProduct(STAFF);
        long firstCategory = fixture.categoryOf(first);
        long secondCategory = fixture.categoryOf(second);

        List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                () -> move(first, secondCategory),
                () -> move(second, firstCategory));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        assertThat(product(first).getCategoryId()).isEqualTo(secondCategory);
        assertThat(product(second).getCategoryId()).isEqualTo(firstCategory);
    }

    @Test
    void reactivationDoesNotReviveDeletedOrHiddenProducts() {
        long deleted = fixture.publishedProduct(STAFF, MANAGER);
        long categoryId = fixture.categoryOf(deleted);
        long hidden = fixture.draftProduct(STAFF);
        move(hidden, categoryId);
        deletion.delete(STAFF, UUID.randomUUID(), Long.toString(deleted), fixture.productVersion(deleted));

        categories.setActive(STAFF, UUID.randomUUID(), Long.toString(categoryId), fixture.category(categoryId).getVersion(),
                false);
        categories.setActive(STAFF, UUID.randomUUID(), Long.toString(categoryId), fixture.category(categoryId).getVersion(),
                true);

        assertThat(product(deleted).isDeleted()).isTrue();
        assertThat(product(deleted).isPublished()).isFalse();
        assertThat(product(hidden).isPublished()).isFalse();
        Boolean active = transaction.execute(status -> categoryRepository.findById(categoryId).orElseThrow().isActive());
        assertThat(active).isTrue();
    }

    private Object move(long productId, long categoryId) {
        Product product = product(productId);
        return updates.update(STAFF, UUID.randomUUID(), Long.toString(productId), product.getVersion(),
                new UpdateProductService.UpdateCommand(product.getName(), product.getDescription(), Long.toString(categoryId)));
    }

    @Test
    void deactivationRacingANewHoldNeverHoldsInAnInactiveCategoryAndKeepsOldHolds() {
        for (int round = 0; round < 3; round++) {
            long productId = fixture.publishedProduct(STAFF, MANAGER);
            long sizeId = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId)).get(0).getId();
            String old = retail.newCheckout();
            retail.hold(old, sizeId, 1);
            long categoryId = fixture.categoryOf(productId);
            long categoryVersion = fixture.category(categoryId).getVersion();
            String racing = retail.newCheckout();

            List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                    () -> categories.setActive(MANAGER, UUID.randomUUID(), Long.toString(categoryId), categoryVersion, false),
                    () -> retail.hold(racing, sizeId, 1));

            assertThat(outcomes.get(0).succeeded()).isTrue();
            if (!outcomes.get(1).succeeded()) {
                assertThat(outcomes.get(1).failure()).isInstanceOfSatisfying(CatalogueSalesException.class,
                        error -> assertThat(error.reason()).isEqualTo(CatalogueSalesException.Reason.NOT_FOR_SALE));
            }
            // After the deactivation no new hold is possible, the old hold is untouched and can still be released.
            String late = retail.newCheckout();
            assertThatThrownBy(() -> retail.hold(late, sizeId, 1)).isInstanceOfSatisfying(CatalogueSalesException.class,
                    error -> assertThat(error.reason()).isEqualTo(CatalogueSalesException.Reason.NOT_FOR_SALE));
            assertThat(retail.holdStatus(old, sizeId)).isEqualTo("HELD");
            retail.release(old, sizeId);
            assertThat(retail.holdStatus(old, sizeId)).isEqualTo("RELEASED");
        }
    }

    private Product product(long productId) {
        return transaction.execute(status -> products.findById(productId).orElseThrow());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
