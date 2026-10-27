package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceApplicationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProposalTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T035 on MySQL: apply exactly the approved price, then publish explicitly; missing prerequisites, sold-out
 * sizes, replay and stale application (FR-015–018).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CataloguePublicationIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private PriceApplicationService applyService;
    @Autowired private ProductPublicationService publication;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private PriceProposalRepository proposals;
    @Autowired private PriceApplicationRepository applications;
    @Autowired private TransactionTemplate transaction;

    @Test
    void approvedThenAppliedThenPublishedIncludingASoldOutSize() {
        long productId = approvedProduct();
        assertThatThrownBy(() -> publish(productId))
                .isInstanceOfSatisfying(CatalogueException.class, error -> {
                    assertThat(error.code()).isEqualTo(CatalogueErrorCode.PUBLICATION_PREREQUISITES_MISSING);
                    assertThat(error.fieldErrors()).hasSize(2);
                });

        applyAll(productId);
        var outcome = publish(productId);

        assertThat(outcome.body().get("status").asText()).isEqualTo("PUBLISHED");
        Product product = transaction.execute(status -> products.findById(productId).orElseThrow());
        assertThat(product.isPublished()).isTrue();
        assertThat(product.getFirstPublishedAt()).isNotNull();
        List<ProductVariant> sizes = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId));
        assertThat(sizes).extracting(v -> v.getAppliedPrice().toString()).containsExactly("150000", "180000");
        assertThat(sizes.get(1).getRemainingUnsold()).isZero();
    }

    @Test
    void applyingTheSameApprovalAgainIsANoOpAndRecordsOneApplication() {
        long productId = approvedProduct();
        PriceProposal proposal = approvedProposals(productId).get(0);

        var first = applyService.apply(STAFF, UUID.randomUUID(), proposal.getId().toString(), proposal.getVersion());
        long versionAfter = fixture.proposal(proposal.getId()).getVersion();
        var second = applyService.apply(STAFF, UUID.randomUUID(), proposal.getId().toString(), versionAfter);

        assertThat(first.body().get("changed").asBoolean()).isTrue();
        assertThat(second.body().get("changed").asBoolean()).isFalse();
        List<?> recorded = transaction.execute(status ->
                applications.findByVariantIdOrderByAppliedSequenceAsc(proposal.getVariantId()));
        assertThat(recorded).hasSize(1);
    }

    @Test
    void pendingProposalCannotBeApplied() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        PriceProposal pending = fixture.proposalsOf(fixture.currentReview(productId).getId()).get(0);

        assertThatThrownBy(() -> applyService.apply(STAFF, UUID.randomUUID(), pending.getId().toString(), pending.getVersion()))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.PRICE_NOT_APPROVED));
    }

    @Test
    void anOlderDecisionCannotOverwriteANewerAppliedPrice() {
        long productId = approvedProduct();
        applyAll(productId);
        // Before the first publication only the initial review's proposals may be applied, so publish first.
        publish(productId);
        long variantId = approvedProposals(productId).get(0).getVariantId();
        Long staleId = transaction.execute(status -> {
            ProductVariant variant = variants.findById(variantId).orElseThrow();
            PriceProposal stale = PriceProposal.draft(variantId, null, 2, BigInteger.valueOf(90_000), 0,
                    ProposalTerms.digest(variant, null), null);
            stale.submit(STAFF, Instant.now());
            stale.approve(MANAGER, Instant.now(), BigInteger.valueOf(90_000));
            return proposals.saveAndFlush(stale).getId();
        });

        assertThatThrownBy(() -> applyService.apply(STAFF, UUID.randomUUID(), staleId.toString(),
                fixture.proposal(staleId).getVersion()))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.PRICE_APPLICATION_STALE));
        BigInteger stillApplied = transaction.execute(status -> variants.findById(variantId).orElseThrow().getAppliedPrice());
        assertThat(stillApplied).isEqualTo(BigInteger.valueOf(150_000));
    }

    @Test
    void hidingKeepsFirstPublicationAndRepeatingAStateIsANoOp() {
        long productId = approvedProduct();
        applyAll(productId);
        publish(productId);
        Instant first = transaction.execute(status -> products.findById(productId).orElseThrow().getFirstPublishedAt());

        assertThat(publish(productId).body().get("changed").asBoolean()).isFalse();
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId),
                fixture.productVersion(productId), false);
        publish(productId);

        Product product = transaction.execute(status -> products.findById(productId).orElseThrow());
        assertThat(product.isPublished()).isTrue();
        assertThat(product.getFirstPublishedAt()).isEqualTo(first);
    }

    @Test
    void staleVersionIsRejected() {
        long productId = approvedProduct();
        applyAll(productId);
        long version = fixture.productVersion(productId);
        assertThatThrownBy(() -> publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId),
                version + 5, true))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.STALE_VERSION));
    }

    private long approvedProduct() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        fixture.approveAll(productId, MANAGER);
        return productId;
    }

    private List<PriceProposal> approvedProposals(long productId) {
        return fixture.proposalsOf(fixture.currentReview(productId).getId());
    }

    private void applyAll(long productId) {
        for (PriceProposal proposal : approvedProposals(productId)) {
            applyService.apply(STAFF, UUID.randomUUID(), proposal.getId().toString(), proposal.getVersion());
        }
    }

    private edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor.Outcome publish(
            long productId) {
        return publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId),
                fixture.productVersion(productId), true);
    }
}
