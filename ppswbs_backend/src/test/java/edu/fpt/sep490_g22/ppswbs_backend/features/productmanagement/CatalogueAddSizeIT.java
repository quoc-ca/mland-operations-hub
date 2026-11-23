package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.VariantBranchStockRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.PublicCatalogueDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchCartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.LoyaltyProductTargetAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceProposalService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductBranchStockService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductBranchStockService.NewSize;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductBranchStockService.SizeAdd;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PublicCatalogueQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FR-069/FR-070 on MySQL 8.4 (Session 2026-11-23): adding sizes after creation — at add-branch time and later at a
 * branch already selling the product; new sizes get rows only where added, a proposed price (independent after the
 * first publication, inside the initial review before it); a size without an applied price stays out of the public
 * read and the FE-08 facts and does not block publishing again.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueAddSizeIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ProductBranchStockService branchStock;
    @Autowired private PriceProposalService priceProposals;
    @Autowired private PriceApplicationService priceApplication;
    @Autowired private ProductPublicationService publication;
    @Autowired private PublicCatalogueQueryService publicCatalogue;
    @Autowired private CatalogueSalesFacade sales;
    @Autowired private ProductVariantRepository variants;
    @Autowired private VariantBranchStockRepository stocks;
    @Autowired private PriceProposalRepository proposals;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;
    @Autowired private DeleteProductService deletion;
    @Autowired private LoyaltyProductTargetAdapter loyaltyTargets;
    @MockitoBean private AccountFacade accounts;

    private long branchA;
    private long branchB;

    @BeforeEach
    void setUp() {
        branchA = fixture.testBranch();
        jdbc.update("INSERT IGNORE INTO workshop_locations (code, display_name, address_snapshot, active) VALUES (?, ?, ?, 1)",
                "PM-TEST-BRANCH-B", "Chi nhánh kiểm thử B", "Địa chỉ B");
        jdbc.update("UPDATE workshop_locations SET active = 1 WHERE code = ?", "PM-TEST-BRANCH-B");
        branchB = jdbc.queryForObject("SELECT id FROM workshop_locations WHERE code = ?", Long.class, "PM-TEST-BRANCH-B");
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addingABranchWithNewSizesKeepsThePublishedProductAndHidesTheUnpricedSize() {
        long productId = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long before = fixture.productVersion(productId);

        branchStock.addBranch(CatalogueTestSupport.STAFF, UUID.randomUUID(), Long.toString(productId), before,
                new ProductBranchStockService.BranchInput(Long.toString(branchB), List.of(
                        new ProductBranchStockService.SizeQuantity(5, "1"),
                        new ProductBranchStockService.SizeQuantity(6, "4"))),
                List.of(new NewSize(6, "210000")));

        ProductVariant size6 = size(productId, 6);
        assertThat(size6.getSku()).endsWith("-S06");
        assertThat(size6.getRemainingUnsold()).isEqualTo(BigInteger.valueOf(4));
        assertThat(size6.getAppliedPrice()).isNull();
        assertThat(rows(size6.getId())).extracting(VariantBranchStock::getLocationId).containsExactly(branchB);
        assertThat(rows(size(productId, 12).getId())).extracting(VariantBranchStock::getLocationId)
                .containsExactlyInAnyOrder(branchA, branchB);
        PriceProposal draft = latestProposal(size6.getId());
        assertThat(draft.getReviewId()).isNull();
        assertThat(draft.getState()).isEqualTo(ReviewState.DRAFT);
        assertThat(draft.getProposedAmount()).isEqualTo(BigInteger.valueOf(210000));
        assertThat(fixture.productVersion(productId)).isGreaterThan(before);

        // FR-070: the product stays public with its priced sizes; the new size is neither shown nor for sale.
        PublicCatalogueDtos.PublicProduct view = publicRead(productId);
        assertThat(view.variants()).extracting(PublicCatalogueDtos.PublicVariant::size).containsExactly(5, 12);
        List<CartSaleFact> facts = transaction.execute(status -> sales.readSaleFacts(List.of(size6.getId())));
        assertThat(facts.getFirst().saleEligible()).isFalse();

        // Submitted, approved and applied: the size shows up without publishing again.
        priceProposals.submit(CatalogueTestSupport.STAFF, UUID.randomUUID(), draft.getId().toString(), draft.getVersion());
        PriceProposal pending = proposal(draft.getId());
        priceProposals.decide(CatalogueTestSupport.MANAGER, UUID.randomUUID(), pending.getId().toString(),
                new PriceProposalService.DecisionCommand(pending.getVersion(), PriceProposalService.Decision.APPROVE,
                        "210000", null));
        PriceProposal approved = proposal(draft.getId());
        priceApplication.apply(CatalogueTestSupport.MANAGER, UUID.randomUUID(), approved.getId().toString(),
                approved.getVersion());
        assertThat(publicRead(productId).variants()).extracting(PublicCatalogueDtos.PublicVariant::size)
                .containsExactly(5, 6, 12);
    }

    @Test
    void addSizesAtABranchCreatesNewSizesAndRowsOfExistingSizesOnlyThere() {
        long productId = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        branchStock.addBranch(CatalogueTestSupport.STAFF, UUID.randomUUID(), Long.toString(productId),
                fixture.productVersion(productId), new ProductBranchStockService.BranchInput(Long.toString(branchB),
                        List.of(new ProductBranchStockService.SizeQuantity(7, "2"))), List.of(new NewSize(7, "190000")));
        long size7 = size(productId, 7).getId();

        // At A: size 7 exists (no row at A) → quantity only; size 8 is new → price.
        branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), Long.toString(productId),
                Long.toString(branchA), fixture.productVersion(productId),
                List.of(new SizeAdd(7, "3", null), new SizeAdd(8, "1", "200000")));

        assertThat(rows(size7)).extracting(VariantBranchStock::getLocationId).containsExactlyInAnyOrder(branchA, branchB);
        assertThat(size(productId, 7).getRemainingUnsold()).isEqualTo(BigInteger.valueOf(5));
        ProductVariant size8 = size(productId, 8);
        assertThat(rows(size8.getId())).extracting(VariantBranchStock::getLocationId).containsExactly(branchA);
        assertThat(proposals(size7)).hasSize(1);
        assertThat(latestProposal(size8.getId()).getProposedAmount()).isEqualTo(BigInteger.valueOf(200000));
        assertThat(fixture.images(productId)).isNotEmpty();
        // Hidden again, then shown again with size 8 still unpriced (FR-017 as changed, FR-070).
        publication.setPublication(CatalogueTestSupport.MANAGER, UUID.randomUUID(), Long.toString(productId),
                fixture.productVersion(productId), false);
        publication.setPublication(CatalogueTestSupport.MANAGER, UUID.randomUUID(), Long.toString(productId),
                fixture.productVersion(productId), true);
        assertThat(publicRead(productId).variants()).extracting(PublicCatalogueDtos.PublicVariant::size)
                .containsExactly(5, 12);
    }

    @Test
    void addingASizeBeforeTheFirstPublicationWithdrawsTheSubmittedReview() {
        long productId = fixture.draftProduct(CatalogueTestSupport.STAFF);
        fixture.addReadyImage(productId, CatalogueTestSupport.STAFF);
        fixture.submit(productId, CatalogueTestSupport.STAFF);
        ProductReview pending = fixture.currentReview(productId);
        assertThat(pending.getState()).isEqualTo(ReviewState.PENDING);

        branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), Long.toString(productId),
                Long.toString(branchA), fixture.productVersion(productId), List.of(new SizeAdd(9, "2", "230000")));

        ProductReview draft = fixture.currentReview(productId);
        assertThat(draft.getRevision()).isEqualTo(pending.getRevision() + 1);
        assertThat(draft.getState()).isEqualTo(ReviewState.DRAFT);
        long size9 = size(productId, 9).getId();
        assertThat(fixture.proposalsOf(draft.getId())).extracting(PriceProposal::getVariantId)
                .contains(size(productId, 5).getId(), size(productId, 12).getId(), size9);
        assertThat(latestProposal(size9).getReviewId()).isEqualTo(draft.getId());
        // The new review can be submitted and approved with the new size inside it (FR-009, FR-010).
        fixture.submit(productId, CatalogueTestSupport.STAFF);
        fixture.approveAll(productId, CatalogueTestSupport.MANAGER);
        assertThat(fixture.currentReview(productId).getState()).isEqualTo(ReviewState.APPROVED);
    }

    @Test
    void badAddSizeCommandsChangeNothing() {
        long productId = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        String id = Long.toString(productId);
        long version = fixture.productVersion(productId);
        long sizesBefore = sizeCount(productId);

        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchA),
                version, List.of(new SizeAdd(5, "1", null))), CatalogueErrorCode.SIZE_ALREADY_EXISTS);
        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchB),
                version, List.of(new SizeAdd(6, "1", "100000"))), CatalogueErrorCode.BRANCH_STOCK_NOT_FOUND);
        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchA),
                version, List.of(new SizeAdd(6, "1", null))), CatalogueErrorCode.VALIDATION_ERROR);
        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchA),
                version, List.of(new SizeAdd(30, "1", "100000"))), CatalogueErrorCode.VALIDATION_ERROR);
        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchA),
                version - 1, List.of(new SizeAdd(6, "1", "100000"))), CatalogueErrorCode.STALE_VERSION);
        // One good line (new size 6) and one bad line (size 12 already sold here): nothing is created.
        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchA),
                version, List.of(new SizeAdd(6, "1", "100000"), new SizeAdd(12, "1", null))),
                CatalogueErrorCode.SIZE_ALREADY_EXISTS);
        assertCode(() -> branchStock.addBranch(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, version,
                new ProductBranchStockService.BranchInput(Long.toString(branchB), List.of()),
                List.of(new NewSize(12, "100000"))), CatalogueErrorCode.SIZE_ALREADY_EXISTS);

        actAsStaff(77L);
        when(accounts.getLocationScope(any())).thenReturn(List.of(branchB));
        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchA),
                version, List.of(new SizeAdd(6, "1", "100000"))), CatalogueErrorCode.BRANCH_OUT_OF_SCOPE);
        SecurityContextHolder.clearContext();

        // T223 (US10 scenario 13): the same size twice in one command, and a branch that is no longer active.
        long rowsBefore = stockRowCount(productId);
        long proposalsBefore = proposalCount(productId);
        long auditsBefore = sizeAddAudits();
        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchA),
                version, List.of(new SizeAdd(6, "1", "100000"), new SizeAdd(6, "2", "100000"))),
                CatalogueErrorCode.VALIDATION_ERROR);
        long closed = inactiveBranch();
        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(closed),
                version, List.of(new SizeAdd(6, "1", "100000"))), CatalogueErrorCode.BRANCH_NOT_FOUND);

        assertThat(sizeCount(productId)).isEqualTo(sizesBefore);
        assertThat(fixture.productVersion(productId)).isEqualTo(version);
        assertThat(stockRowCount(productId)).isEqualTo(rowsBefore);
        assertThat(proposalCount(productId)).isEqualTo(proposalsBefore);
        assertThat(sizeAddAudits()).isEqualTo(auditsBefore);
    }

    @Test
    void anExistingSizeSentWithAPriceOrADeletedProductChangeNothing() {
        // US10 scenario 10: size 7 exists but has no row at B; it is added there with a quantity only, never a new price.
        long productId = sizeSevenAtBranchAOnly();
        String id = Long.toString(productId);
        long version = fixture.productVersion(productId);
        long rowsBefore = stockRowCount(productId);
        long proposalsBefore = proposalCount(productId);
        long auditsBefore = sizeAddAudits();
        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchB),
                version, List.of(new SizeAdd(7, "1", "100000"))), CatalogueErrorCode.VALIDATION_ERROR);
        assertThat(rows(size(productId, 7).getId())).extracting(VariantBranchStock::getLocationId).containsExactly(branchA);
        assertThat(stockRowCount(productId)).isEqualTo(rowsBefore);
        assertThat(proposalCount(productId)).isEqualTo(proposalsBefore);
        assertThat(sizeAddAudits()).isEqualTo(auditsBefore);
        assertThat(fixture.productVersion(productId)).isEqualTo(version);

        // US10 scenario 13: a deleted product takes no new size.
        deletion.delete(CatalogueTestSupport.MANAGER, UUID.randomUUID(), id, version);
        long deletedVersion = fixture.productVersion(productId);
        assertCode(() -> branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchA),
                deletedVersion, List.of(new SizeAdd(6, "1", "100000"))), CatalogueErrorCode.PRODUCT_DELETED);
        assertThat(sizeCount(productId)).isEqualTo(3);
    }

    @Test
    void aStockOnlyAddKeepsTheProductVersionAndTheReview() {
        // FR-069: adding a row for a size the product already has creates no size, so neither version nor review moves.
        long productId = sizeSevenAtBranchAOnly();
        String id = Long.toString(productId);
        long version = fixture.productVersion(productId);
        ProductReview review = fixture.currentReview(productId);
        long proposalsBefore = proposalCount(productId);

        branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchB), version,
                List.of(new SizeAdd(7, "2", null)));

        assertThat(rows(size(productId, 7).getId())).extracting(VariantBranchStock::getLocationId)
                .containsExactlyInAnyOrder(branchA, branchB);
        assertThat(fixture.productVersion(productId)).isEqualTo(version);
        ProductReview after = fixture.currentReview(productId);
        assertThat(after.getId()).isEqualTo(review.getId());
        assertThat(after.getState()).isEqualTo(review.getState());
        assertThat(proposalCount(productId)).isEqualTo(proposalsBefore);
        assertThat(sizeAddAudits()).isPositive();
    }

    @Test
    void anApprovedReviewGoesBackToDraftAndADraftReviewTakesTheNewSizeInPlace() {
        // US10 scenario 12: approved but never published → a new size reopens the review as a draft.
        long productId = fixture.draftProduct(CatalogueTestSupport.STAFF);
        fixture.addReadyImage(productId, CatalogueTestSupport.STAFF);
        fixture.submit(productId, CatalogueTestSupport.STAFF);
        fixture.approveAll(productId, CatalogueTestSupport.MANAGER);
        ProductReview approved = fixture.currentReview(productId);
        assertThat(approved.getState()).isEqualTo(ReviewState.APPROVED);

        branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), Long.toString(productId),
                Long.toString(branchA), fixture.productVersion(productId), List.of(new SizeAdd(9, "2", "230000")));
        ProductReview draft = fixture.currentReview(productId);
        assertThat(draft.getState()).isEqualTo(ReviewState.DRAFT);
        assertThat(draft.getRevision()).isEqualTo(approved.getRevision() + 1);

        // FR-011: while the review is a draft, another new size joins that same draft.
        branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), Long.toString(productId),
                Long.toString(branchA), fixture.productVersion(productId), List.of(new SizeAdd(10, "1", "240000")));
        ProductReview same = fixture.currentReview(productId);
        assertThat(same.getId()).isEqualTo(draft.getId());
        assertThat(same.getRevision()).isEqualTo(draft.getRevision());
        assertThat(same.getState()).isEqualTo(ReviewState.DRAFT);
        long size10 = size(productId, 10).getId();
        assertThat(fixture.proposalsOf(same.getId())).extracting(PriceProposal::getVariantId)
                .contains(size(productId, 9).getId(), size10);
        assertThat(latestProposal(size10).getReviewId()).isEqualTo(same.getId());
    }

    @Test
    void anUnpricedSizeIsLeftOutOfBranchFactsLockedContextsPublicFiltersAndPromotionTargets() {
        // T227 (FR-070): every reader treats a size without an applied price as not public and not for sale.
        long productId = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        PublicCatalogueDtos.PublicProduct before = publicRead(productId);
        branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), Long.toString(productId),
                Long.toString(branchA), fixture.productVersion(productId), List.of(new SizeAdd(8, "3", "1")));
        long size8 = size(productId, 8).getId();
        long size5 = size(productId, 5).getId();

        List<BranchCartSaleFact> branchFacts = transaction.execute(status ->
                sales.readBranchSaleFacts(List.of(new BranchVariant(size8, branchA), new BranchVariant(size5, branchA))));
        assertThat(branchFacts).filteredOn(fact -> fact.variantId() == size8).singleElement()
                .satisfies(fact -> assertThat(fact.saleEligible()).isFalse());
        assertThat(branchFacts).filteredOn(fact -> fact.variantId() == size5).singleElement()
                .satisfies(fact -> assertThat(fact.saleEligible()).isTrue());

        assertSales(() -> transaction.execute(status -> sales.withLockedSaleContext(List.of(size5, size8), context -> context)), size8);
        assertSales(() -> transaction.execute(status -> sales.withLockedBranchSaleContext(
                List.of(new BranchVariant(size5, branchA), new BranchVariant(size8, branchA)), context -> context)), size8);

        // The public list: same price range as before (the 1 ₫ draft price of size 8 is no price), and the price and
        // can-buy filters do not match on the unpriced size.
        PublicCatalogueDtos.PublicProduct listed = listed(before.name(), null, null, null);
        assertThat(listed.priceRange()).isEqualTo(before.priceRange());
        assertThat(listed.variants()).extracting(PublicCatalogueDtos.PublicVariant::size).doesNotContain(8);
        assertThat(listedIds(before.name(), "0", "1", null)).doesNotContain(Long.toString(productId));
        assertThat(listedIds(before.name(), null, null, true)).contains(Long.toString(productId));

        TargetFact target = loyaltyTargets.validateAndGuard(TargetScope.PRODUCT,
                List.of(new TargetRef(TargetDimension.VARIANT, Long.toString(size8)))).getFirst();
        assertThat(target.exists()).isTrue();
        assertThat(target.publicVisible()).isFalse();
        TargetFact priced = loyaltyTargets.validateAndGuard(TargetScope.PRODUCT,
                List.of(new TargetRef(TargetDimension.VARIANT, Long.toString(size5)))).getFirst();
        assertThat(priced.publicVisible()).isTrue();
    }

    @Test
    void aRetryWithTheSameKeyReplaysTheResult() {
        long productId = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        UUID key = UUID.randomUUID();
        long version = fixture.productVersion(productId);
        List<SizeAdd> lines = List.of(new SizeAdd(10, "1", "250000"));
        branchStock.addSizes(CatalogueTestSupport.STAFF, key, Long.toString(productId), Long.toString(branchA), version, lines);
        branchStock.addSizes(CatalogueTestSupport.STAFF, key, Long.toString(productId), Long.toString(branchA), version, lines);
        assertThat(sizeCount(productId)).isEqualTo(3);
    }

    // ---- helpers ----

    private PublicCatalogueDtos.PublicProduct publicRead(long productId) {
        return publicCatalogue.get(Long.toString(productId));
    }

    private ProductVariant size(long productId, int size) {
        return transaction.execute(status -> variants.findByProductIdInOrderByProductIdAscRingSizeAsc(List.of(productId)))
                .stream().filter(variant -> variant.getRingSize() == size).findFirst().orElseThrow();
    }

    private long sizeCount(long productId) {
        return transaction.execute(status -> variants.findByProductIdInOrderByProductIdAscRingSizeAsc(List.of(productId))).size();
    }

    private List<VariantBranchStock> rows(long variantId) {
        return transaction.execute(status -> stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(List.of(variantId)));
    }

    private PriceProposal latestProposal(long variantId) {
        return transaction.execute(status -> proposals.findFirstByVariantIdOrderByRevisionDescIdDesc(variantId).orElseThrow());
    }

    private List<PriceProposal> proposals(long variantId) {
        return transaction.execute(status -> proposals.findByVariantIdOrderByRevisionAscIdAsc(variantId));
    }

    private PriceProposal proposal(long id) {
        return fixture.proposal(id);
    }

    /** Published product sold at A and B (sizes 5 and 12 at both), then size 7 added at A only. */
    private long sizeSevenAtBranchAOnly() {
        long productId = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        String id = Long.toString(productId);
        branchStock.addBranch(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, fixture.productVersion(productId),
                new ProductBranchStockService.BranchInput(Long.toString(branchB),
                        List.of(new ProductBranchStockService.SizeQuantity(5, "1"))), List.of());
        branchStock.addSizes(CatalogueTestSupport.STAFF, UUID.randomUUID(), id, Long.toString(branchA),
                fixture.productVersion(productId), List.of(new SizeAdd(7, "1", "190000")));
        assertThat(rows(size(productId, 7).getId())).extracting(VariantBranchStock::getLocationId).containsExactly(branchA);
        return productId;
    }

    private long stockRowCount(long productId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM catalogue_variant_branch_stock s JOIN catalogue_variants v ON v.id = s.variant_id"
                + " WHERE v.product_id = ?", Long.class, productId);
    }

    private long proposalCount(long productId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM catalogue_price_proposals p JOIN catalogue_variants v ON v.id = p.variant_id"
                + " WHERE v.product_id = ?", Long.class, productId);
    }

    private long sizeAddAudits() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM catalogue_audit_events WHERE action = ? AND outcome = 'SUCCESS'",
                Long.class, ProductBranchStockService.SIZE_ADD_ACTION);
    }

    private long inactiveBranch() {
        jdbc.update("INSERT IGNORE INTO workshop_locations (code, display_name, address_snapshot, active) VALUES (?, ?, ?, 0)",
                "PM-TEST-BRANCH-CLOSED", "Chi nhánh kiểm thử đã đóng", "Địa chỉ C");
        jdbc.update("UPDATE workshop_locations SET active = 0 WHERE code = ?", "PM-TEST-BRANCH-CLOSED");
        return jdbc.queryForObject("SELECT id FROM workshop_locations WHERE code = ?", Long.class, "PM-TEST-BRANCH-CLOSED");
    }

    private PublicCatalogueDtos.PublicProduct listed(String name, String minimum, String maximum, Boolean canBuy) {
        return publicCatalogue.list(new PublicCatalogueQueryService.Filter(name, null, minimum, maximum, canBuy, null, null))
                .items().stream().filter(item -> item.name().equals(name)).findFirst().orElseThrow();
    }

    private List<String> listedIds(String name, String minimum, String maximum, Boolean canBuy) {
        return publicCatalogue.list(new PublicCatalogueQueryService.Filter(name, null, minimum, maximum, canBuy, null, null))
                .items().stream().map(PublicCatalogueDtos.PublicProduct::id).toList();
    }

    private static void assertSales(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, long variantId) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogueSalesException.class, error -> {
            assertThat(error.reason()).isEqualTo(CatalogueSalesException.Reason.NOT_FOR_SALE);
            assertThat(error.variantIds()).contains(variantId);
        });
    }

    private static void assertCode(Runnable action, CatalogueErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(CatalogueException.class)
                .satisfies(error -> assertThat(((CatalogueException) error).code()).isEqualTo(code));
    }

    private static void actAsStaff(long accountId) {
        CurrentAccountActor actor = new CurrentAccountActor(accountId, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    }
}
