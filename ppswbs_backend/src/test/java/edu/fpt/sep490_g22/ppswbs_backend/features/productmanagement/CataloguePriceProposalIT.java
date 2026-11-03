package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceApplicationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceProposalService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
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
 * T052 on MySQL: independent price proposals after the first publication (create, revise, submit, decide,
 * apply), one pending per size, the applied price kept while waiting, and proposals before the first
 * publication going through the initial review (FR-011/013–016). Role checks belong to T007.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CataloguePriceProposalIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private PriceProposalService proposals;
    @Autowired private PriceApplicationService applyService;
    @Autowired private ProductVariantRepository variants;
    @Autowired private PriceApplicationRepository applications;
    @Autowired private TransactionTemplate transaction;

    @Test
    void proposeSubmitApproveThenApplyChangesThePriceOnlyWhenApplied() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size5 = sizes(productId).get(0);

        long proposalId = id(proposals.create(STAFF, UUID.randomUUID(), size5.getId().toString(), size5.getVersion(),
                "170000"));
        assertThat(fixture.proposal(proposalId).getState()).isEqualTo(ReviewState.DRAFT);
        proposals.submit(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());
        assertThat(sizes(productId).get(0).getPendingProposalId()).isEqualTo(proposalId);

        ProductVariant pendingSize = sizes(productId).get(0);
        assertThatThrownBy(() -> proposals.create(STAFF, UUID.randomUUID(), pendingSize.getId().toString(),
                pendingSize.getVersion(), "160000"))
                .isInstanceOfSatisfying(CatalogueException.class, error ->
                        assertThat(error.code()).isEqualTo(CatalogueErrorCode.PENDING_PRICE_PROPOSAL_EXISTS));

        proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(proposalId), new PriceProposalService.DecisionCommand(
                fixture.proposal(proposalId).getVersion(), PriceProposalService.Decision.APPROVE, "165000", null));
        PriceProposal approved = fixture.proposal(proposalId);
        assertThat(approved.getState()).isEqualTo(ReviewState.APPROVED);
        assertThat(approved.getApprovedAmount()).isEqualTo(BigInteger.valueOf(165_000));
        assertThat(approved.getDecidedBy()).isEqualTo(MANAGER);
        ProductVariant waiting = sizes(productId).get(0);
        assertThat(waiting.getPendingProposalId()).isNull();
        assertThat(waiting.getAppliedPrice()).isEqualTo(BigInteger.valueOf(150_000));

        applyService.apply(STAFF, UUID.randomUUID(), Long.toString(proposalId), approved.getVersion());

        ProductVariant applied = sizes(productId).get(0);
        assertThat(applied.getAppliedPrice()).isEqualTo(BigInteger.valueOf(165_000));
        assertThat(applied.getAppliedProposalId()).isEqualTo(proposalId);
        assertThat(applied.getAppliedSequence()).isEqualTo(2);
        List<?> history = transaction.execute(status -> applications.findByVariantIdOrderByAppliedSequenceAsc(applied.getId()));
        assertThat(history).hasSize(2);
    }

    @Test
    void rejectionNeedsAReasonCarriesNoAmountAndKeepsTheAppliedPrice() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        long proposalId = submittedProposal(productId, "190000");
        long version = fixture.proposal(proposalId).getVersion();

        assertThatThrownBy(() -> proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(proposalId),
                new PriceProposalService.DecisionCommand(version, PriceProposalService.Decision.REJECT, null, " ")))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(proposalId),
                new PriceProposalService.DecisionCommand(version, PriceProposalService.Decision.REJECT, "1", "Cao quá")))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.VALIDATION_ERROR));

        proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(proposalId),
                new PriceProposalService.DecisionCommand(version, PriceProposalService.Decision.REJECT, null, "Cao quá"));

        PriceProposal rejected = fixture.proposal(proposalId);
        assertThat(rejected.getState()).isEqualTo(ReviewState.REJECTED);
        assertThat(rejected.getReason()).isEqualTo("Cao quá");
        assertThat(sizes(productId).get(0).getAppliedPrice()).isEqualTo(BigInteger.valueOf(150_000));
        assertThatThrownBy(() -> applyService.apply(STAFF, UUID.randomUUID(), Long.toString(proposalId),
                fixture.proposal(proposalId).getVersion()))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.PRICE_NOT_APPROVED));
    }

    @Test
    void revisingAPendingProposalWithdrawsItAndStartsANewDraftRevision() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        long pendingId = submittedProposal(productId, "190000");

        long revisedId = id(proposals.revise(STAFF, UUID.randomUUID(), Long.toString(pendingId),
                fixture.proposal(pendingId).getVersion(), "185000"));

        PriceProposal old = fixture.proposal(pendingId);
        PriceProposal revised = fixture.proposal(revisedId);
        assertThat(old.getState()).isEqualTo(ReviewState.PENDING);
        assertThat(old.isEffective()).isFalse();
        assertThat(revised.getState()).isEqualTo(ReviewState.DRAFT);
        assertThat(revised.getRevision()).isEqualTo(old.getRevision() + 1);
        assertThat(revised.getSupersedesId()).isEqualTo(pendingId);
        assertThat(sizes(productId).get(0).getPendingProposalId()).isNull();
        assertThatThrownBy(() -> proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(pendingId),
                new PriceProposalService.DecisionCommand(old.getVersion(), PriceProposalService.Decision.APPROVE,
                        "190000", null)))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.STALE_VERSION));
    }

    @Test
    void sameKeyReplaysAndAStaleVariantVersionIsRejected() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size12 = sizes(productId).get(1);
        UUID key = UUID.randomUUID();

        var first = proposals.create(STAFF, key, size12.getId().toString(), size12.getVersion(), "200000");
        var replay = proposals.create(STAFF, key, size12.getId().toString(), size12.getVersion(), "200000");

        assertThat(first.status()).isEqualTo(201);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.resourceId()).isEqualTo(first.resourceId());
        assertThatThrownBy(() -> proposals.create(STAFF, UUID.randomUUID(), size12.getId().toString(),
                size12.getVersion() + 7, "200000"))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.STALE_VERSION));
    }

    @Test
    void initialReviewProposalsAreDecidedOnlyWithTheWholeReview() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        PriceProposal pending = fixture.proposalsOf(fixture.currentReview(productId).getId()).get(0);

        assertThatThrownBy(() -> proposals.decide(MANAGER, UUID.randomUUID(), pending.getId().toString(),
                new PriceProposalService.DecisionCommand(pending.getVersion(), PriceProposalService.Decision.APPROVE,
                        "150000", null)))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.REVIEW_NOT_CURRENT));
        assertThatThrownBy(() -> proposals.submit(STAFF, UUID.randomUUID(), pending.getId().toString(),
                pending.getVersion()))
                .isInstanceOf(CatalogueException.class);
    }

    @Test
    void beforeFirstPublicationANewPriceWithdrawsTheApprovalWithoutInheritingIt() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        fixture.approveAll(productId, MANAGER);
        ProductReview approved = fixture.currentReview(productId);
        ProductVariant size5 = sizes(productId).get(0);

        long proposalId = id(proposals.create(STAFF, UUID.randomUUID(), size5.getId().toString(), size5.getVersion(),
                "155000"));

        ProductReview current = fixture.currentReview(productId);
        assertThat(current.getRevision()).isEqualTo(approved.getRevision() + 1);
        assertThat(current.getState()).isEqualTo(ReviewState.DRAFT);
        PriceProposal created = fixture.proposal(proposalId);
        assertThat(created.getReviewId()).isEqualTo(current.getId());
        assertThat(created.getProposedAmount()).isEqualTo(BigInteger.valueOf(155_000));
        assertThat(created.getApprovedAmount()).isNull();
        assertThat(fixture.proposalsOf(approved.getId())).allSatisfy(old -> assertThat(old.isEffective()).isFalse());
        assertThat(fixture.proposalsOf(current.getId())).hasSize(2)
                .allSatisfy(next -> assertThat(next.getState()).isEqualTo(ReviewState.DRAFT));
    }

    private long submittedProposal(long productId, String amount) {
        ProductVariant size5 = sizes(productId).get(0);
        long proposalId = id(proposals.create(STAFF, UUID.randomUUID(), size5.getId().toString(), size5.getVersion(),
                amount));
        proposals.submit(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());
        return proposalId;
    }

    private List<ProductVariant> sizes(long productId) {
        return transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId));
    }

    private static long id(edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor.Outcome outcome) {
        return Long.parseLong(outcome.resourceId());
    }
}
