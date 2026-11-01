package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceProposalService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.Concurrently;
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
 * T052 on MySQL, completing CataloguePriceProposalIT: one pending proposal per size also while the initial review
 * is pending, no second submission while one is pending (also when sent at the same time), a Manager deciding a
 * proposal they made themselves with no AI input, and an older approval never overwriting a newer applied price
 * (FR-013–016/026–028). Staff deciding a price over HTTP is refused in CatalogueApiIT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CataloguePriceIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private PriceProposalService proposals;
    @Autowired private PriceApplicationService applyService;
    @Autowired private ProductVariantRepository variants;
    @Autowired private TransactionTemplate transaction;

    @Test
    void aSizeUnderAPendingInitialReviewGetsNoIndependentProposal() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        ProductVariant size = sizes(productId).get(0);

        assertCode(() -> proposals.create(STAFF, UUID.randomUUID(), size.getId().toString(), size.getVersion(), "99000"),
                CatalogueErrorCode.PENDING_PRICE_PROPOSAL_EXISTS);
    }

    @Test
    void twoSubmissionsOfTheSameDraftAtOnceLeaveExactlyOnePending() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size = sizes(productId).get(0);
        long draftId = Long.parseLong(proposals.create(STAFF, UUID.randomUUID(), size.getId().toString(),
                size.getVersion(), "160000").resourceId());
        long version = fixture.proposal(draftId).getVersion();

        List<Concurrently.Outcome<CatalogueCommandExecutor.Outcome>> outcomes = Concurrently.run(
                () -> proposals.submit(STAFF, UUID.randomUUID(), Long.toString(draftId), version),
                () -> proposals.submit(MANAGER, UUID.randomUUID(), Long.toString(draftId), version));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(sizes(productId).get(0).getPendingProposalId()).isEqualTo(draftId);
        assertThat(fixture.proposal(draftId).getState()).isEqualTo(ReviewState.PENDING);
    }

    @Test
    void aManagerMayDecideTheirOwnProposalWithoutAnySuggestion() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size = sizes(productId).get(0);
        long proposalId = Long.parseLong(proposals.create(MANAGER, UUID.randomUUID(), size.getId().toString(),
                size.getVersion(), "175000").resourceId());
        proposals.submit(MANAGER, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());

        proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(proposalId), new PriceProposalService.DecisionCommand(
                fixture.proposal(proposalId).getVersion(), PriceProposalService.Decision.APPROVE, "172000", null));

        PriceProposal decided = fixture.proposal(proposalId);
        assertThat(decided.getState()).isEqualTo(ReviewState.APPROVED);
        assertThat(decided.getSubmittedBy()).isEqualTo(MANAGER);
        assertThat(decided.getDecidedBy()).isEqualTo(MANAGER);
        assertThat(decided.getApprovedAmount()).isEqualTo(BigInteger.valueOf(172_000));
    }

    @Test
    void anOlderApprovalCannotOverwriteANewerAppliedPrice() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size = sizes(productId).get(0);
        long older = approved(size, "140000");
        // A newer proposal is approved and applied first: it supersedes the older one.
        long newer = approved(sizes(productId).get(0), "145000");
        applyService.apply(STAFF, UUID.randomUUID(), Long.toString(newer), fixture.proposal(newer).getVersion());

        assertThatThrownBy(() -> applyService.apply(STAFF, UUID.randomUUID(), Long.toString(older),
                fixture.proposal(older).getVersion()))
                .isInstanceOf(CatalogueException.class);
        assertThat(sizes(productId).get(0).getAppliedPrice()).isEqualTo(BigInteger.valueOf(145_000));
    }

    private long approved(ProductVariant size, String amount) {
        long id = Long.parseLong(proposals.create(STAFF, UUID.randomUUID(), size.getId().toString(), size.getVersion(),
                amount).resourceId());
        proposals.submit(STAFF, UUID.randomUUID(), Long.toString(id), fixture.proposal(id).getVersion());
        proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(id), new PriceProposalService.DecisionCommand(
                fixture.proposal(id).getVersion(), PriceProposalService.Decision.APPROVE, amount, null));
        return id;
    }

    private List<ProductVariant> sizes(long productId) {
        return transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId));
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable action, CatalogueErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
