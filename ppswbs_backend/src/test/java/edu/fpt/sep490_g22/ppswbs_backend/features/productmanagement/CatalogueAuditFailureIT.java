package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceApplicationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuditWriter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueValues;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductCategoryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductReviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/**
 * T029 / T035 / T075 on MySQL: a success audit that cannot be written rolls the whole command back — no partial
 * review decision, no applied price, no publication, no category, and no receipt (FR-026–028).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueAuditFailureIT {

    @MockitoSpyBean private CatalogueAuditWriter audit;
    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ProductReviewService reviews;
    @Autowired private PriceApplicationService applyService;
    @Autowired private ProductPublicationService publication;
    @Autowired private ProductCategoryService categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private ProductCategoryRepository categoryRepository;
    @Autowired private PriceApplicationRepository applications;
    @Autowired private CatalogueCommandReceiptRepository receipts;
    @Autowired private TransactionTemplate transaction;

    @Test
    void aFailedAuditLeavesTheReviewAndEveryProposalPending() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        failAuditOf(ProductReviewService.DECIDE_ACTION);
        UUID key = UUID.randomUUID();

        ProductReview review = fixture.currentReview(productId);
        assertThatThrownBy(() -> reviews.decide(MANAGER, key, Long.toString(productId),
                new ProductReviewService.DecisionCommand(review.getId().toString(), review.getRevision(),
                        review.getContentVersion(), ProductReviewService.Decision.REJECT, "Lý do", List.of())))
                .isInstanceOf(IllegalStateException.class);

        assertThat(fixture.currentReview(productId).getState()).isEqualTo(ReviewState.PENDING);
        assertThat(fixture.proposalsOf(review.getId())).allMatch(PriceProposal::isCurrentPending);
        assertNoReceipt(MANAGER, ProductReviewService.DECIDE_ACTION, key);
    }

    @Test
    void aFailedAuditLeavesThePriceUnappliedAndTheProductHidden() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        fixture.approveAll(productId, MANAGER);
        PriceProposal proposal = fixture.proposalsOf(fixture.currentReview(productId).getId()).get(0);
        failAuditOf(PriceApplicationService.APPLY_ACTION);
        UUID applyKey = UUID.randomUUID();

        assertThatThrownBy(() -> applyService.apply(STAFF, applyKey, proposal.getId().toString(), proposal.getVersion()))
                .isInstanceOf(IllegalStateException.class);
        java.math.BigInteger applied = transaction.execute(status -> variants.findById(proposal.getVariantId())
                .orElseThrow().getAppliedPrice());
        assertThat(applied).isNull();
        List<?> recorded = transaction.execute(status ->
                applications.findByVariantIdOrderByAppliedSequenceAsc(proposal.getVariantId()));
        assertThat(recorded).isEmpty();
        assertNoReceipt(STAFF, PriceApplicationService.APPLY_ACTION, applyKey);
    }

    @Test
    void aFailedAuditLeavesTheProductUnpublished() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                false);
        failAuditOf(ProductPublicationService.PUBLISH_ACTION);

        assertThatThrownBy(() -> publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId),
                fixture.productVersion(productId), true))
                .isInstanceOf(IllegalStateException.class);
        Boolean published = transaction.execute(status -> products.findById(productId).orElseThrow().isPublished());
        assertThat(published).isFalse();
    }

    @Test
    void aFailedAuditCreatesNoCategory() {
        String name = "Audit hỏng " + UUID.randomUUID();
        failAuditOf(ProductCategoryService.CREATE_ACTION);

        assertThatThrownBy(() -> categories.create(STAFF, UUID.randomUUID(), name, null))
                .isInstanceOf(IllegalStateException.class);
        Boolean exists = transaction.execute(status -> categoryRepository.existsByNameKey(CatalogueValues.categoryNameKey(name)));
        assertThat(exists).isFalse();
    }

    /** recordSuccess requires an existing transaction (MANDATORY), so even the stubbing call runs inside one. */
    private void failAuditOf(String action) {
        transaction.executeWithoutResult(status -> doThrow(new IllegalStateException("audit store down")).when(audit)
                .recordSuccess(argThat(entry -> entry != null && action.equals(entry.action()))));
    }

    private void assertNoReceipt(String actor, String action, UUID key) {
        Boolean present = transaction.execute(status ->
                receipts.findByActorIdAndActionAndCommandId(actor, action, key).isPresent());
        assertThat(present).isFalse();
    }
}
