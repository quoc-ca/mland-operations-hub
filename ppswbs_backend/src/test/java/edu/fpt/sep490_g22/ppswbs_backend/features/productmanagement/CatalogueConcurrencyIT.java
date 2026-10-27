package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceApplicationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductReviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.Concurrently;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Part of T084 on MySQL with real concurrent transactions: delete vs publish, the same command key sent twice
 * at once, and two applications of one approval (FR-025/026/028). Races with new holds, quantity and accepted
 * orders need the real FE-08 adapter (T003/T019) and are not covered here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueConcurrencyIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ProductPublicationService publication;
    @Autowired private DeleteProductService deletion;
    @Autowired private CreateProductService createProducts;
    @Autowired private PriceApplicationService applyService;
    @Autowired private ProductReviewService reviews;
    @Autowired private ProductRepository products;
    @Autowired private PriceApplicationRepository applications;
    @Autowired private CatalogueCommandReceiptRepository receipts;
    @Autowired private TransactionTemplate transaction;

    @Test
    void deleteAndPublishAtTheSameTimeNeverLeaveADeletedProductPublished() {
        for (int round = 0; round < 3; round++) {
            long productId = fixture.publishedProduct(STAFF, MANAGER);
            publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId),
                    fixture.productVersion(productId), false);
            long version = fixture.productVersion(productId);

            List<Concurrently.Outcome<CatalogueCommandExecutor.Outcome>> outcomes = Concurrently.run(
                    () -> deletion.delete(MANAGER, UUID.randomUUID(), Long.toString(productId), version),
                    () -> publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId), version, true));

            assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome ->
                    assertThat(outcome.failure()).isInstanceOfSatisfying(CatalogueException.class, error ->
                            assertThat(error.code()).isIn(CatalogueErrorCode.STALE_VERSION,
                                    CatalogueErrorCode.PRODUCT_DELETED)));
            Product after = product(productId);
            assertThat(after.isDeleted() && after.isPublished()).isFalse();
            assertThat(after.isDeleted()).isEqualTo(outcomes.get(0).succeeded());
        }
    }

    @Test
    void theSameCommandKeySentTwiceAtOnceCreatesOneProductAndOneReceipt() {
        UUID key = UUID.randomUUID();
        String name = "Gửi lặp " + UUID.randomUUID();
        var command = new CreateProductService.CreateProductCommand(name, "Mô tả",
                Long.toString(fixture.activeCategory()),
                List.of(new CreateProductService.VariantInput(6, "120000", "1")), List.of());

        List<Concurrently.Outcome<CatalogueCommandExecutor.Outcome>> outcomes = Concurrently.run(
                () -> createProducts.create(STAFF, key, command),
                () -> createProducts.create(STAFF, key, command));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        assertThat(outcomes.get(0).value().resourceId()).isEqualTo(outcomes.get(1).value().resourceId());
        assertThat(outcomes).filteredOn(outcome -> outcome.value().replayed()).hasSize(1);
        Long created = transaction.execute(status -> products.findAll().stream()
                .filter(product -> product.getName().equals(name)).count());
        assertThat(created).isEqualTo(1L);
        boolean oneReceipt = Boolean.TRUE.equals(transaction.execute(status -> receipts
                .findByActorIdAndActionAndCommandId(STAFF, CreateProductService.CREATE_ACTION, key).isPresent()));
        assertThat(oneReceipt).isTrue();
    }

    @Test
    void twoApplicationsOfOneApprovalAtTheSameTimeApplyItOnce() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        fixture.approveAll(productId, MANAGER);
        PriceProposal proposal = fixture.proposalsOf(fixture.currentReview(productId).getId()).get(0);

        List<Concurrently.Outcome<CatalogueCommandExecutor.Outcome>> outcomes = Concurrently.run(
                () -> applyService.apply(STAFF, UUID.randomUUID(), proposal.getId().toString(), proposal.getVersion()),
                () -> applyService.apply(MANAGER, UUID.randomUUID(), proposal.getId().toString(), proposal.getVersion()));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        assertThat(outcomes).extracting(outcome -> outcome.value().body().get("changed").asBoolean())
                .containsExactlyInAnyOrder(true, false);
        List<?> recorded = transaction.execute(status ->
                applications.findByVariantIdOrderByAppliedSequenceAsc(proposal.getVariantId()));
        assertThat(recorded).hasSize(1);
    }

    /** Regression: after waiting for the product lock the loser must see the winner's committed review. */
    @Test
    void twoSubmissionsAtTheSameTimeSubmitOnce() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        long version = fixture.productVersion(productId);

        List<Concurrently.Outcome<CatalogueCommandExecutor.Outcome>> outcomes = Concurrently.run(
                () -> reviews.submit(STAFF, UUID.randomUUID(), Long.toString(productId), version),
                () -> reviews.submit(MANAGER, UUID.randomUUID(), Long.toString(productId), version));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome ->
                assertThat(outcome.failure()).isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.REVIEW_NOT_CURRENT)));
        assertThat(fixture.proposalsOf(fixture.currentReview(productId).getId()))
                .filteredOn(PriceProposal::isCurrentPending).hasSize(2);
    }

    private Product product(long productId) {
        return transaction.execute(status -> products.findById(productId).orElseThrow());
    }
}
