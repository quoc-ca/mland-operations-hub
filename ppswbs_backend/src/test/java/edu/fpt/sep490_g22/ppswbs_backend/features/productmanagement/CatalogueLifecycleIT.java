package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T059 on MySQL, completing CatalogueSaleStatusAndDeleteIT: soft delete from every not-deleted state, an old
 * command key replayed after delete returns its old receipt without resurrecting anything, and republishing a
 * hidden product re-checks every condition (FR-018/021–025). Cancelling the confirmation dialog sends no command,
 * so it has no backend case.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueLifecycleIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private DeleteProductService deletion;
    @Autowired private ProductPublicationService publication;
    @Autowired private ProductRepository products;
    @Autowired private CatalogueMediaRepository media;
    @Autowired private TransactionTemplate transaction;

    @Test
    void everyNotDeletedStateCanBeDeletedAndStaysDeleted() {
        Map<String, Supplier<Long>> states = new LinkedHashMap<>();
        states.put("draft", () -> fixture.draftProduct(STAFF));
        states.put("pending", () -> {
            long id = fixture.draftProduct(STAFF);
            fixture.addReadyImage(id, STAFF);
            fixture.submit(id, STAFF);
            return id;
        });
        states.put("approved", () -> {
            long id = fixture.draftProduct(STAFF);
            fixture.addReadyImage(id, STAFF);
            fixture.submit(id, STAFF);
            fixture.approveAll(id, MANAGER);
            return id;
        });
        states.put("public", () -> fixture.publishedProduct(STAFF, MANAGER));
        states.put("hidden", () -> {
            long id = fixture.publishedProduct(STAFF, MANAGER);
            publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(id), fixture.productVersion(id), false);
            return id;
        });

        states.forEach((state, create) -> {
            long productId = create.get();
            deletion.delete(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId));
            Product deleted = product(productId);
            assertThat(deleted.isDeleted()).as(state).isTrue();
            assertThat(deleted.isPublished()).as(state).isFalse();
            assertThatThrownBy(() -> publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId),
                    deleted.getVersion(), true))
                    .as(state)
                    .isInstanceOfSatisfying(CatalogueException.class,
                            error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.PRODUCT_DELETED));
        });
    }

    @Test
    void replayingAnOldPublishKeyAfterDeleteDoesNotResurrectTheProduct() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                false);
        UUID oldKey = UUID.randomUUID();
        long version = fixture.productVersion(productId);
        publication.setPublication(STAFF, oldKey, Long.toString(productId), version, true);
        deletion.delete(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId));

        CatalogueCommandExecutor.Outcome replay = publication.setPublication(STAFF, oldKey, Long.toString(productId),
                version, true);

        assertThat(replay.replayed()).isTrue();
        Product after = product(productId);
        assertThat(after.isDeleted()).isTrue();
        assertThat(after.isPublished()).isFalse();
    }

    @Test
    void republishingAHiddenProductChecksEveryConditionAgain() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        Instant firstPublished = product(productId).getFirstPublishedAt();
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                false);
        UUID image = fixture.images(productId).get(0).getMediaObjectId();
        transaction.executeWithoutResult(status -> media.findById(image).orElseThrow().markUnavailable(Instant.now()));

        assertThatThrownBy(() -> publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId),
                fixture.productVersion(productId), true))
                .isInstanceOfSatisfying(CatalogueException.class, error -> {
                    assertThat(error.code()).isEqualTo(CatalogueErrorCode.PUBLICATION_PREREQUISITES_MISSING);
                    assertThat(error.fieldErrors()).extracting(field -> field.field()).containsExactly("images");
                });

        transaction.executeWithoutResult(status -> media.findById(image).orElseThrow()
                .markReady("asset-" + image, "2", "webp", Instant.now()));
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                true);
        assertThat(product(productId).isPublished()).isTrue();
        assertThat(product(productId).getFirstPublishedAt()).isEqualTo(firstPublished);
    }

    private Product product(long productId) {
        return transaction.execute(status -> products.findById(productId).orElseThrow());
    }
}
