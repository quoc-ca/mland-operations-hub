package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceProposalService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductReviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantSaleStatusService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T059 on MySQL: stopping/resuming sale of one size and soft delete (FR-020–025). Holds and accepted
 * orders belong to FE-08 (T062/T065); role checks belong to T007.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueSaleStatusAndDeleteIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private VariantSaleStatusService saleStatus;
    @Autowired private DeleteProductService deletion;
    @Autowired private ProductPublicationService publication;
    @Autowired private PriceProposalService proposals;
    @Autowired private PriceApplicationService applyService;
    @Autowired private ProductReviewService reviews;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private TransactionTemplate transaction;

    @Test
    void disablingOneSizeKeepsQuantityPricePublicationAndTheOtherSize() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size5 = sizes(productId).get(0);

        var disabled = saleStatus.setSaleEnabled(STAFF, UUID.randomUUID(), size5.getId().toString(),
                size5.getVersion(), false);

        assertThat(disabled.body().get("status").asText()).isEqualTo("DISABLED");
        List<ProductVariant> after = sizes(productId);
        assertThat(after.get(0).isSaleEnabled()).isFalse();
        assertThat(after.get(0).getRemainingUnsold()).isEqualTo(BigInteger.valueOf(3));
        assertThat(after.get(0).getAppliedPrice()).isEqualTo(BigInteger.valueOf(150_000));
        assertThat(after.get(0).getSku()).isEqualTo(size5.getSku());
        assertThat(after.get(1).isSaleEnabled()).isTrue();
        assertThat(product(productId).isPublished()).isTrue();

        var again = saleStatus.setSaleEnabled(STAFF, UUID.randomUUID(), size5.getId().toString(), 0, false);
        assertThat(again.body().get("changed").asBoolean()).isFalse();

        var enabled = saleStatus.setSaleEnabled(MANAGER, UUID.randomUUID(), size5.getId().toString(),
                after.get(0).getVersion(), true);
        assertThat(enabled.body().get("status").asText()).isEqualTo("ENABLED");
        assertThat(sizes(productId).get(0).isSaleEnabled()).isTrue();
    }

    @Test
    void staleVariantVersionIsRejected() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size5 = sizes(productId).get(0);

        assertCode(() -> saleStatus.setSaleEnabled(STAFF, UUID.randomUUID(), size5.getId().toString(),
                size5.getVersion() + 3, false), CatalogueErrorCode.STALE_VERSION);
        assertThat(sizes(productId).get(0).isSaleEnabled()).isTrue();
    }

    @Test
    void deletingAPublishedProductHidesItKeepsHistoryAndBlocksEveryLaterCommand() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        Product before = product(productId);
        List<ProductVariant> sizesBefore = sizes(productId);
        PriceProposal approved = fixture.proposalsOf(fixture.currentReview(productId).getId()).get(0);
        UUID key = UUID.randomUUID();

        var deleted = deletion.delete(MANAGER, key, Long.toString(productId), before.getVersion());

        assertThat(deleted.body().get("status").asText()).isEqualTo("DELETED");
        Product after = product(productId);
        assertThat(after.isDeleted()).isTrue();
        assertThat(after.getDeletedBy()).isEqualTo(MANAGER);
        assertThat(after.isPublished()).isFalse();
        assertThat(after.getFirstPublishedAt()).isEqualTo(before.getFirstPublishedAt());
        assertThat(sizes(productId)).extracting(ProductVariant::getSku)
                .containsExactlyElementsOf(sizesBefore.stream().map(ProductVariant::getSku).toList());
        assertThat(sizes(productId)).extracting(ProductVariant::getRemainingUnsold)
                .containsExactlyElementsOf(sizesBefore.stream().map(ProductVariant::getRemainingUnsold).toList());

        assertThat(deletion.delete(MANAGER, key, Long.toString(productId), before.getVersion()).replayed()).isTrue();
        assertThat(deletion.delete(STAFF, UUID.randomUUID(), Long.toString(productId), after.getVersion())
                .body().get("changed").asBoolean()).isFalse();

        long version = after.getVersion();
        ProductVariant size5 = sizes(productId).get(0);
        assertCode(() -> publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId), version, true),
                CatalogueErrorCode.PRODUCT_DELETED);
        assertCode(() -> saleStatus.setSaleEnabled(STAFF, UUID.randomUUID(), size5.getId().toString(),
                size5.getVersion(), false), CatalogueErrorCode.PRODUCT_DELETED);
        assertCode(() -> proposals.create(STAFF, UUID.randomUUID(), size5.getId().toString(), size5.getVersion(),
                "999000"), CatalogueErrorCode.PRODUCT_DELETED);
        assertCode(() -> applyService.apply(STAFF, UUID.randomUUID(), approved.getId().toString(),
                fixture.proposal(approved.getId()).getVersion()), CatalogueErrorCode.PRODUCT_DELETED);
    }

    @Test
    void deletingADraftUnderReviewStopsTheDecision() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        ProductReview review = fixture.currentReview(productId);

        deletion.delete(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId));

        assertCode(() -> reviews.decide(MANAGER, UUID.randomUUID(), Long.toString(productId),
                new ProductReviewService.DecisionCommand(review.getId().toString(), review.getRevision(),
                        review.getContentVersion(), ProductReviewService.Decision.REJECT, "x", List.of())),
                CatalogueErrorCode.PRODUCT_DELETED);
        assertThat(fixture.currentReview(productId).getState()).isEqualTo(review.getState());
    }

    @Test
    void staleVersionDoesNotDelete() {
        long productId = fixture.draftProduct(STAFF);
        long version = fixture.productVersion(productId);

        assertCode(() -> deletion.delete(STAFF, UUID.randomUUID(), Long.toString(productId), version + 1),
                CatalogueErrorCode.STALE_VERSION);
        assertThat(product(productId).isDeleted()).isFalse();
    }

    private Product product(long productId) {
        return transaction.execute(status -> products.findById(productId).orElseThrow());
    }

    private List<ProductVariant> sizes(long productId) {
        return transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId));
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable action, CatalogueErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
