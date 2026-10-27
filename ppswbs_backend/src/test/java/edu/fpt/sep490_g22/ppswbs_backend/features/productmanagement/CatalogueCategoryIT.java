package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductCategoryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T075 on MySQL: unique normalized names including inactive categories, edit with version, impact and
 * activation that changes eligibility but never {@code product.published} (FR-029/030). Role checks
 * (Staff or Manager with category.manage) belong to T007.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueCategoryIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ProductCategoryService categories;
    @Autowired private ProductPublicationService publication;
    @Autowired private ProductRepository products;
    @Autowired private TransactionTemplate transaction;

    @Test
    void createTrimsKeepsTheDisplayNameAndRejectsANameDifferingOnlyByCase() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        var created = categories.create(STAFF, UUID.randomUUID(), "  Nhẫn Cưới " + unique + "  ", "  ");

        assertThat(created.status()).isEqualTo(201);
        ProductCategory category = fixture.category(Long.parseLong(created.resourceId()));
        assertThat(category.getName()).isEqualTo("Nhẫn Cưới " + unique);
        assertThat(category.getDescription()).isNull();
        assertThat(category.isActive()).isTrue();

        assertCode(() -> categories.create(MANAGER, UUID.randomUUID(), "NHẪN CƯỚI " + unique.toUpperCase(), null),
                CatalogueErrorCode.DUPLICATE_CATEGORY_NAME);
        var accentDiffers = categories.create(MANAGER, UUID.randomUUID(), "Nhan Cuoi " + unique, null);
        assertThat(accentDiffers.status()).isEqualTo(201);
    }

    @Test
    void anInactiveCategoryStillReservesItsName() {
        String name = "Danh mục ngừng " + UUID.randomUUID();
        long id = Long.parseLong(categories.create(STAFF, UUID.randomUUID(), name, null).resourceId());
        categories.setActive(STAFF, UUID.randomUUID(), Long.toString(id), fixture.category(id).getVersion(), false);

        assertCode(() -> categories.create(STAFF, UUID.randomUUID(), name.toLowerCase(), null),
                CatalogueErrorCode.DUPLICATE_CATEGORY_NAME);
    }

    @Test
    void updateNeedsTheCurrentVersionAndKeepsNamesUnique() {
        long first = fixture.activeCategory();
        long second = fixture.activeCategory();
        String takenName = fixture.category(first).getName();
        long version = fixture.category(second).getVersion();

        assertCode(() -> categories.update(STAFF, UUID.randomUUID(), Long.toString(second), version, takenName, null),
                CatalogueErrorCode.DUPLICATE_CATEGORY_NAME);
        assertCode(() -> categories.update(STAFF, UUID.randomUUID(), Long.toString(second), version + 1, "Khác", null),
                CatalogueErrorCode.STALE_VERSION);

        String upper = fixture.category(second).getName().toUpperCase();
        var renamed = categories.update(STAFF, UUID.randomUUID(), Long.toString(second), version, upper, "Mô tả");
        assertThat(renamed.body().get("changed").asBoolean()).isTrue();
        ProductCategory after = fixture.category(second);
        assertThat(after.getName()).isEqualTo(upper);
        assertThat(after.getDescription()).isEqualTo("Mô tả");
        var same = categories.update(STAFF, UUID.randomUUID(), Long.toString(second), after.getVersion(), upper, "Mô tả");
        assertThat(same.body().get("changed").asBoolean()).isFalse();
    }

    @Test
    void impactListsTheAffectedProductsWithTheCurrentVersion() {
        long productId = fixture.draftProduct(STAFF);
        long categoryId = fixture.categoryOf(productId);

        var impact = categories.impact(Long.toString(categoryId), 0);

        assertThat(impact.active()).isTrue();
        assertThat(impact.version()).isEqualTo(fixture.category(categoryId).getVersion());
        assertThat(impact.affectedProducts().totalElements()).isEqualTo("1");
        assertThat(impact.affectedProducts().items().get(0).id()).isEqualTo(Long.toString(productId));
    }

    @Test
    void deactivationBlocksPublicationWithoutChangingThePublishedFlagAndReactivationRestoresIt() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        long categoryId = fixture.categoryOf(productId);

        categories.setActive(STAFF, UUID.randomUUID(), Long.toString(categoryId),
                fixture.category(categoryId).getVersion(), false);

        assertThat(fixture.category(categoryId).isActive()).isFalse();
        assertThat(published(productId)).isTrue();
        assertThat(categories.setActive(STAFF, UUID.randomUUID(), Long.toString(categoryId), 0, false)
                .body().get("changed").asBoolean()).isFalse();

        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId),
                fixture.productVersion(productId), false);
        assertThatThrownBy(() -> publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId),
                fixture.productVersion(productId), true))
                .isInstanceOfSatisfying(CatalogueException.class, error -> {
                    assertThat(error.code()).isEqualTo(CatalogueErrorCode.PUBLICATION_PREREQUISITES_MISSING);
                    assertThat(error.fieldErrors()).extracting(field -> field.field()).containsExactly("categoryId");
                });

        categories.setActive(MANAGER, UUID.randomUUID(), Long.toString(categoryId),
                fixture.category(categoryId).getVersion(), true);
        assertThat(published(productId)).isFalse();
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId),
                fixture.productVersion(productId), true);
        assertThat(published(productId)).isTrue();
    }

    @Test
    void staleActivationVersionIsRejected() {
        long categoryId = fixture.activeCategory();
        long version = fixture.category(categoryId).getVersion();

        assertCode(() -> categories.setActive(STAFF, UUID.randomUUID(), Long.toString(categoryId), version + 1, false),
                CatalogueErrorCode.STALE_VERSION);
        assertThat(fixture.category(categoryId).isActive()).isTrue();
    }

    private boolean published(long productId) {
        Boolean published = transaction.execute(status -> products.findById(productId).orElseThrow().isPublished());
        return Boolean.TRUE.equals(published);
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable action, CatalogueErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
