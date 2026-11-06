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
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.RealRetailHolds;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaCleanupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService.ImageInput;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductImageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Part of T084 on MySQL with real concurrent transactions: delete vs publish, the same command key sent twice
 * at once, and two applications of one approval (FR-025/026/028). Races with new holds, quantity and accepted
 * orders run against the REAL FE-08 source ({@link RealRetailHolds}, develop b0e7796); FE-08's payment flow is not
 * on develop yet, so the settlement plays that caller through FE-08's own hold services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import({CatalogueFlowFixture.class, RealRetailHolds.class})
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
    @Autowired private RealRetailHolds retail;
    @Autowired private CatalogueSalesFacade sales;
    @Autowired private CatalogueMediaCleanupService cleanup;
    @Autowired private ProductImageService imageService;
    /** Stand-in for the image provider only (the real Cloudinary call is T051); holds stay real. */
    @MockitoBean private MediaStoragePort storage;
    @Autowired private ProductVariantRepository variants;

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

    @Test
    void deleteRacingANewHoldNeverHoldsADeletedProductAndOldHoldsCanStillBeSettled() {
        for (int round = 0; round < 3; round++) {
            long productId = fixture.publishedProduct(STAFF, MANAGER);
            long sizeId = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId)).get(0).getId();
            String old = retail.newCheckout();
            retail.hold(old, sizeId, 1);
            long version = fixture.productVersion(productId);
            String racing = retail.newCheckout();

            List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                    () -> deletion.delete(MANAGER, UUID.randomUUID(), Long.toString(productId), version),
                    () -> retail.hold(racing, sizeId, 1));

            assertThat(outcomes.get(0).succeeded()).isTrue();
            assertThat(product(productId).isDeleted()).isTrue();
            if (!outcomes.get(1).succeeded()) {
                assertThat(outcomes.get(1).failure()).isInstanceOfSatisfying(CatalogueSalesException.class,
                        error -> assertThat(error.reason()).isEqualTo(CatalogueSalesException.Reason.NOT_FOR_SALE));
            }
            // The hold accepted before deletion is settled and sold once through the historical path.
            UUID sale = UUID.randomUUID();
            retail.settleAndRecord(old, sizeId, 1, sale);
            retail.settleAndRecord(old, sizeId, 1, sale);
            assertThat(retail.holdStatus(old, sizeId)).isEqualTo("SETTLED");
            java.math.BigInteger remaining = transaction.execute(status -> variants.findById(sizeId).orElseThrow().getRemainingUnsold());
            assertThat(remaining)
                    .isEqualTo(java.math.BigInteger.TWO);
        }
    }

    @Test
    void deleteRacingApprovalOrPriceApplicationLeavesAConsistentProduct() {
        for (int round = 0; round < 3; round++) {
            // Delete vs approve of the pending review.
            long reviewed = fixture.draftProduct(STAFF);
            fixture.addReadyImage(reviewed, STAFF);
            fixture.submit(reviewed, STAFF);
            long reviewedVersion = fixture.productVersion(reviewed);
            List<Concurrently.Outcome<Object>> approve = Concurrently.run(
                    () -> deletion.delete(MANAGER, UUID.randomUUID(), Long.toString(reviewed), reviewedVersion),
                    () -> {
                        fixture.approveAll(reviewed, MANAGER);
                        return null;
                    });
            assertExpectedFailures(approve);
            assertThat(approve).anyMatch(Concurrently.Outcome::succeeded);
            assertThat(product(reviewed).isPublished()).isFalse();

            // Delete vs application of an approved price.
            long approved = fixture.draftProduct(STAFF);
            fixture.addReadyImage(approved, STAFF);
            fixture.submit(approved, STAFF);
            fixture.approveAll(approved, MANAGER);
            PriceProposal proposal = fixture.proposalsOf(fixture.currentReview(approved).getId()).get(0);
            long approvedVersion = fixture.productVersion(approved);
            List<Concurrently.Outcome<Object>> apply = Concurrently.run(
                    () -> deletion.delete(MANAGER, UUID.randomUUID(), Long.toString(approved), approvedVersion),
                    () -> applyService.apply(STAFF, UUID.randomUUID(), proposal.getId().toString(), proposal.getVersion()));
            assertExpectedFailures(apply);
            assertThat(apply).anyMatch(Concurrently.Outcome::succeeded);
            Product after = product(approved);
            assertThat(after.isDeleted() && after.isPublished()).isFalse();
            if (!apply.get(1).succeeded()) {
                List<?> recorded = transaction.execute(status ->
                        applications.findByVariantIdOrderByAppliedSequenceAsc(proposal.getVariantId()));
                assertThat(recorded).isEmpty();
            }
        }
    }

    private static void assertExpectedFailures(List<Concurrently.Outcome<Object>> outcomes) {
        outcomes.stream().filter(outcome -> !outcome.succeeded()).forEach(outcome ->
                assertThat(outcome.failure()).isInstanceOfSatisfying(CatalogueException.class, error ->
                        assertThat(error.code()).isIn(CatalogueErrorCode.STALE_VERSION, CatalogueErrorCode.PRODUCT_DELETED,
                                CatalogueErrorCode.REVIEW_NOT_CURRENT)));
    }

    @Test
    void retainRacingCleanupNeverKeepsAReferenceToADeletedImage() {
        for (int round = 0; round < 5; round++) {
            Instant now = Instant.now();
            // Never attached for 25 hours: eligible for cleanup until something references it.
            UUID image = fixture.readyMedia(STAFF, now.minus(Duration.ofHours(25)));
            String owner = "order-item:" + UUID.randomUUID();

            List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                    () -> cleanup.clean(image, now),
                    () -> transaction.execute(status -> {
                        sales.retainMediaReferences(owner, List.of(image));
                        return "retained";
                    }));

            CatalogueMediaObject after = fixture.media(image);
            if (outcomes.get(1).succeeded()) {
                assertThat(outcomes.get(0).value()).isEqualTo(CatalogueMediaCleanupService.Result.SKIPPED);
                assertThat(after.getState()).isEqualTo(CatalogueMediaObject.State.READY);
            } else {
                assertThat(outcomes.get(1).failure()).isInstanceOfSatisfying(CatalogueSalesException.class,
                        error -> assertThat(error.reason()).isEqualTo(CatalogueSalesException.Reason.MEDIA_NOT_AVAILABLE));
                assertThat(after.getState()).isEqualTo(CatalogueMediaObject.State.DELETED);
            }
        }
    }

    @Test
    void attachRacingCleanupNeverShowsADeletedImageOnAProduct() {
        for (int round = 0; round < 5; round++) {
            Instant now = Instant.now();
            long productId = fixture.draftProduct(STAFF);
            UUID image = fixture.readyMedia(STAFF, now.minus(Duration.ofHours(25)));
            long version = fixture.productVersion(productId);

            List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                    () -> cleanup.clean(image, now),
                    () -> imageService.replace(STAFF, UUID.randomUUID(), Long.toString(productId), version,
                            List.of(new ImageInput(image, "Ảnh", 0))));

            CatalogueMediaObject after = fixture.media(image);
            boolean onProduct = fixture.images(productId).stream().anyMatch(row -> row.getMediaObjectId().equals(image));
            if (outcomes.get(1).succeeded()) {
                assertThat(onProduct).isTrue();
                assertThat(after.getState()).isEqualTo(CatalogueMediaObject.State.READY);
            } else {
                assertThat(outcomes.get(1).failure()).isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.MEDIA_NOT_FOUND));
                assertThat(onProduct).isFalse();
                assertThat(after.getState()).isEqualTo(CatalogueMediaObject.State.DELETED);
            }
        }
    }

    private Product product(long productId) {
        return transaction.execute(status -> products.findById(productId).orElseThrow());
    }
}
