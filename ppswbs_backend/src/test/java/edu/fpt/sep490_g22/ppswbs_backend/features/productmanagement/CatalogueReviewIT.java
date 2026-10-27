package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueLockCoordinator;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductReviewInvalidationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductReviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductCategoryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.UpdateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T029 on MySQL: whole-review submission and decision, self-approval, reject reason, stale decisions,
 * one pending proposal per size, replay and invalidation before the first publication (FR-009–011/013).
 * Role checks (Staff cannot approve) belong to the authorization boundary of T007 and are tested there.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueReviewIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ProductReviewService reviews;
    @Autowired private ProductReviewInvalidationService invalidation;
    @Autowired private CatalogueLockCoordinator locks;
    @Autowired private ProductVariantRepository variants;
    @Autowired private TransactionTemplate transaction;
    @Autowired private ProductCategoryService categories;
    @Autowired private UpdateProductService updates;
    @Autowired private ProductReviewRepository reviewRepository;

    @Test
    void submitFreezesTheWholeFileAndPointsOnePendingProposalPerSize() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);

        var outcome = reviews.submit(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId));

        ProductReview review = fixture.currentReview(productId);
        assertThat(outcome.body().get("status").asText()).isEqualTo("PENDING");
        assertThat(review.getState()).isEqualTo(ReviewState.PENDING);
        assertThat(review.getSnapshot()).contains("\"sku\"").contains("proposalRevision").contains("Mặt trước");
        List<PriceProposal> proposals = fixture.proposalsOf(review.getId());
        assertThat(proposals).hasSize(2).allMatch(PriceProposal::isCurrentPending);
        List<ProductVariant> sizes = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId));
        assertThat(sizes).extracting(ProductVariant::getPendingProposalId)
                .containsExactlyInAnyOrderElementsOf(proposals.stream().map(PriceProposal::getId).toList());
    }

    @Test
    void submitWithoutUsableImageIsRejectedAndChangesNothing() {
        long productId = fixture.draftProduct(STAFF);

        assertThatThrownBy(() -> fixture.submit(productId, STAFF))
                .isInstanceOfSatisfying(CatalogueException.class, error -> {
                    assertThat(error.code()).isEqualTo(CatalogueErrorCode.VALIDATION_ERROR);
                    assertThat(error.fieldErrors()).extracting(field -> field.field()).contains("images");
                });
        assertThat(fixture.currentReview(productId).getState()).isEqualTo(ReviewState.DRAFT);
    }

    @Test
    void sameKeyReplaysAndASecondSubmissionIsNotCurrent() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        UUID key = UUID.randomUUID();
        long version = fixture.productVersion(productId);

        reviews.submit(STAFF, key, Long.toString(productId), version);
        assertThat(reviews.submit(STAFF, key, Long.toString(productId), version).replayed()).isTrue();
        assertThatThrownBy(() -> reviews.submit(STAFF, UUID.randomUUID(), Long.toString(productId), version))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.REVIEW_NOT_CURRENT));
    }

    @Test
    void managerMaySelfApproveWithAdjustedFinalAmounts() {
        long productId = fixture.draftProduct(MANAGER);
        fixture.addReadyImage(productId, MANAGER);
        fixture.submit(productId, MANAGER);
        ProductReview review = fixture.currentReview(productId);
        List<PriceProposal> pending = fixture.proposalsOf(review.getId());

        reviews.decide(MANAGER, UUID.randomUUID(), Long.toString(productId), new ProductReviewService.DecisionCommand(
                review.getId().toString(), review.getRevision(), review.getContentVersion(),
                ProductReviewService.Decision.APPROVE, null, pending.stream()
                .map(p -> new ProductReviewService.DecisionPrice(p.getVariantId().toString(), p.getId().toString(), "149000"))
                .toList()));

        assertThat(fixture.currentReview(productId).getState()).isEqualTo(ReviewState.APPROVED);
        assertThat(fixture.proposalsOf(review.getId())).allSatisfy(proposal -> {
            assertThat(proposal.getState()).isEqualTo(ReviewState.APPROVED);
            assertThat(proposal.getApprovedAmount()).hasToString("149000");
            assertThat(proposal.getDecidedBy()).isEqualTo(MANAGER);
        });
        List<ProductVariant> sizes = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId));
        assertThat(sizes).allSatisfy(variant -> {
            assertThat(variant.getPendingProposalId()).isNull();
            assertThat(variant.getAppliedPrice()).isNull();
        });
    }

    @Test
    void approvalMustCoverEverySizeAndNothingIsPartiallyApproved() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        ProductReview review = fixture.currentReview(productId);
        PriceProposal onlyOne = fixture.proposalsOf(review.getId()).get(0);

        assertThatThrownBy(() -> reviews.decide(MANAGER, UUID.randomUUID(), Long.toString(productId),
                new ProductReviewService.DecisionCommand(review.getId().toString(), review.getRevision(),
                        review.getContentVersion(), ProductReviewService.Decision.APPROVE, null,
                        List.of(new ProductReviewService.DecisionPrice(onlyOne.getVariantId().toString(),
                                onlyOne.getId().toString(), "150000")))))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.VALIDATION_ERROR));
        assertThat(fixture.currentReview(productId).getState()).isEqualTo(ReviewState.PENDING);
        assertThat(fixture.proposalsOf(review.getId())).allMatch(PriceProposal::isCurrentPending);
    }

    @Test
    void rejectionNeedsAReasonAndRejectsEverySize() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        ProductReview review = fixture.currentReview(productId);
        var blank = new ProductReviewService.DecisionCommand(review.getId().toString(), review.getRevision(),
                review.getContentVersion(), ProductReviewService.Decision.REJECT, "  ", List.of());
        assertThatThrownBy(() -> reviews.decide(MANAGER, UUID.randomUUID(), Long.toString(productId), blank))
                .isInstanceOf(CatalogueException.class);

        reviews.decide(MANAGER, UUID.randomUUID(), Long.toString(productId), new ProductReviewService.DecisionCommand(
                review.getId().toString(), review.getRevision(), review.getContentVersion(),
                ProductReviewService.Decision.REJECT, "Ảnh chưa rõ", List.of()));

        assertThat(fixture.currentReview(productId).getRejectionReason()).isEqualTo("Ảnh chưa rõ");
        assertThat(fixture.proposalsOf(review.getId())).allSatisfy(proposal ->
                assertThat(proposal.getState()).isEqualTo(ReviewState.REJECTED));
    }

    @Test
    void decisionOnAnOldContentVersionIsNotCurrent() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        ProductReview review = fixture.currentReview(productId);

        assertThatThrownBy(() -> reviews.decide(MANAGER, UUID.randomUUID(), Long.toString(productId),
                new ProductReviewService.DecisionCommand(review.getId().toString(), review.getRevision(),
                        review.getContentVersion() + 1, ProductReviewService.Decision.REJECT, "x", List.of())))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.REVIEW_NOT_CURRENT));
    }

    @Test
    void anInactiveCategoryBlocksSubmission() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        long categoryId = fixture.categoryOf(productId);
        categories.setActive(STAFF, UUID.randomUUID(), Long.toString(categoryId), fixture.category(categoryId).getVersion(),
                false);

        assertThatThrownBy(() -> fixture.submit(productId, STAFF))
                .isInstanceOfSatisfying(CatalogueException.class, error -> {
                    assertThat(error.code()).isEqualTo(CatalogueErrorCode.VALIDATION_ERROR);
                    assertThat(error.fieldErrors()).extracting(field -> field.field()).contains("categoryId");
                });
        assertThat(fixture.currentReview(productId).getState()).isEqualTo(ReviewState.DRAFT);
    }

    @Test
    void theSubmittedSnapshotDoesNotDriftWhenTheProductIsEditedLater() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        ProductReview submitted = fixture.currentReview(productId);
        String snapshot = submitted.getSnapshot();
        String newName = "Sau khi gửi " + UUID.randomUUID();

        updates.update(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                new UpdateProductService.UpdateCommand(newName, "Mô tả khác", Long.toString(fixture.categoryOf(productId))));

        ProductReview old = transaction.execute(status -> reviewRepository.findById(submitted.getId()).orElseThrow());
        assertThat(old.getSnapshot()).isEqualTo(snapshot).doesNotContain(newName);
        assertThat(old.isEffective()).isFalse();
        assertThat(fixture.currentReview(productId).getRevision()).isEqualTo(submitted.getRevision() + 1);
    }

    @Test
    void editingBeforeFirstPublicationWithdrawsTheApprovalAndStartsANewDraftRevision() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        fixture.approveAll(productId, MANAGER);
        ProductReview approved = fixture.currentReview(productId);

        transaction.executeWithoutResult(status -> {
            var locked = locks.lockProduct(productId);
            locked.product().updateContent("Tên mới " + UUID.randomUUID(), "Mô tả", locked.product().getCategoryId());
            invalidation.invalidateBeforeFirstPublication(locked.product(), locked.variants(), Map.of());
        });

        ProductReview current = fixture.currentReview(productId);
        assertThat(current.getRevision()).isEqualTo(approved.getRevision() + 1);
        assertThat(current.getState()).isEqualTo(ReviewState.DRAFT);
        assertThat(fixture.proposalsOf(approved.getId())).allSatisfy(old -> {
            assertThat(old.getState()).isEqualTo(ReviewState.APPROVED);
            assertThat(old.isEffective()).isFalse();
        });
        assertThat(fixture.proposalsOf(current.getId())).hasSize(2).allSatisfy(next -> {
            assertThat(next.getState()).isEqualTo(ReviewState.DRAFT);
            assertThat(next.getRevision()).isEqualTo(2);
            assertThat(next.getSupersedesId()).isNotNull();
        });
    }
}
