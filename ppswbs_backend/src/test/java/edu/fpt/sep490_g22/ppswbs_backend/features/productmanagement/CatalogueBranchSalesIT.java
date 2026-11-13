package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.VariantBranchStockRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchAcceptedSaleResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchCartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException.Reason;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.HeldSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductBranchStockService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.RealRetailHolds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-053 contract (2026-11-09) on MySQL 8.4 with the REAL FE-08 hold source, which does not track holds per branch
 * yet: the {@link AvailabilityGuardPort} defaults report the size's holds for every branch. Covers branch cart facts,
 * the locked branch context, the deduction at a branch (idempotent, conflicting reuse refused, below zero refused)
 * and a product not yet assigned to any branch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import({CatalogueFlowFixture.class, RealRetailHolds.class})
class CatalogueBranchSalesIT {

    private static final String STAFF = CatalogueTestSupport.STAFF;
    private static final String MANAGER = CatalogueTestSupport.MANAGER;

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private RealRetailHolds retail;
    @Autowired private CatalogueSalesFacade sales;
    @Autowired private AvailabilityGuardPort source;
    @Autowired private ProductBranchStockService branchStock;
    @Autowired private ProductVariantRepository variants;
    @Autowired private VariantBranchStockRepository stocks;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;
    @Autowired private edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PublicCatalogueQueryService publicCatalogue;
    @Autowired private edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ManagementProductQueryService managementProducts;

    private long branchA;
    private long branchB;
    private long branchC;

    @BeforeEach
    void branches() {
        branchA = fixture.testBranch();
        branchB = branch("PM-TEST-BRANCH-B");
        branchC = branch("PM-TEST-BRANCH-C");
    }

    @Test
    void theFe08SourceTracksBranchesAndCountsALegacySizeHoldAtEveryBranch() {
        long size5 = publishedAtAAndB().getId();
        retail.hold(retail.newCheckout(), size5, 1);   // legacy size-level hold (no branch)

        // FE-08 T124 (2026-11-12): holds are per size x branch; a size-level hold still counts at every branch.
        assertThat(source.tracksHoldsPerBranch()).isTrue();
        Map<BranchVariant, HeldSnapshot> snapshots = transaction.execute(status -> source.readBranchSnapshots(
                List.of(new BranchVariant(size5, branchB), new BranchVariant(size5, branchA)), Instant.now()));
        // AvailabilityGuardPort.readBranchSnapshots promises exactly one snapshot per requested key, not an order
        // (CatalogueAvailabilityService puts them in ascending BranchVariant order itself).
        assertThat(snapshots.keySet()).containsExactlyInAnyOrder(
                new BranchVariant(size5, branchA), new BranchVariant(size5, branchB));
        assertThat(snapshots.values()).allSatisfy(snapshot -> assertThat(snapshot.heldQuantity()).isEqualTo(BigInteger.ONE));
    }

    @Test
    void theManagementReadShowsHoldsAndAvailabilityPerBranch() {
        ProductVariant size5 = publishedAtAAndB();   // size 5: A = 3, B = 2
        retail.holdAtBranch(retail.newCheckout(), size5.getId(), branchA, 2);

        // FR-051 (2026-11-12): a hold at A counts at A only; the size row keeps the total of its holds.
        var variant = managementProducts.get(Long.toString(size5.getProductId())).variants().stream()
                .filter(row -> row.id().equals(Long.toString(size5.getId()))).findFirst().orElseThrow();
        assertThat(variant.validHeldQuantity()).isEqualTo("2");
        assertThat(variant.branchStocks()).extracting(stock -> stock.locationId(), stock -> stock.validHeldQuantity(),
                        stock -> stock.availableQuantity())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(Long.toString(branchA), "2", "1"),
                        org.assertj.core.groups.Tuple.tuple(Long.toString(branchB), "0", "2"));
    }

    @Test
    void cartFactsArePerBranchAndNeverAboveTheSizeAvailability() {
        long size5 = publishedAtAAndB().getId();   // size 5: A = 3, B = 2; total 5
        retail.hold(retail.newCheckout(), size5, 1);

        List<BranchCartSaleFact> facts = sales.readBranchSaleFacts(List.of(
                new BranchVariant(size5, branchC), new BranchVariant(size5, branchA), new BranchVariant(size5, branchB)));

        assertThat(facts).extracting(BranchCartSaleFact::locationId).containsExactly(branchA, branchB, branchC);
        // per-size source: the 1 held counts at every branch (upper bound) → A 3−1, B 2−1; C does not sell the size
        assertThat(facts).extracting(BranchCartSaleFact::availableQuantity)
                .containsExactly(BigInteger.TWO, BigInteger.ONE, BigInteger.ZERO);
        assertThat(facts).extracting(BranchCartSaleFact::saleEligible).containsExactly(true, true, false);
        assertThat(facts).allSatisfy(fact -> assertThat(fact.assignedToBranches()).isTrue());
    }

    @Test
    void aStoppedBranchIsNotForSaleInTheLockedContextButTheOtherBranchIs() {
        ProductVariant size5 = publishedAtAAndB();
        branchStock.setSaleEnabled(STAFF, UUID.randomUUID(), size5.getProductId().toString(), Long.toString(branchB), false);

        BigInteger atA = transaction.execute(status -> sales.withLockedBranchSaleContext(
                List.of(new BranchVariant(size5.getId(), branchA)),
                context -> context.branch(size5.getId(), branchA).orElseThrow().remainingUnsold()));
        assertThat(atA).isEqualTo(BigInteger.valueOf(3));

        assertReason(() -> transaction.execute(status -> sales.withLockedBranchSaleContext(
                List.of(new BranchVariant(size5.getId(), branchB)), context -> null)), Reason.NOT_FOR_SALE);
        assertReason(() -> transaction.execute(status -> sales.withLockedBranchSaleContext(
                List.of(new BranchVariant(size5.getId(), branchC)), context -> null)), Reason.NOT_FOR_SALE);
    }

    @Test
    void aSaleAtABranchTakesFromThatBranchAndTheTotalOnceAndRefusesReuseForAnotherBranch() {
        ProductVariant size5 = publishedAtAAndB();
        UUID operation = UUID.randomUUID();
        BranchVariant atB = new BranchVariant(size5.getId(), branchB);

        BranchAcceptedSaleResult first = sell(atB, operation, 2);
        BranchAcceptedSaleResult again = sell(atB, operation, 2);

        assertThat(first.branchRemainingAfter()).isZero();
        assertThat(first.sizeRemainingAfter()).isEqualTo(BigInteger.valueOf(3));
        assertThat(again.replayed()).isTrue();
        assertThat(again.branchRemainingAfter()).isZero();
        assertThat(rows(size5.getId())).containsExactly(BigInteger.valueOf(3), BigInteger.ZERO);
        assertThat(total(size5.getId())).isEqualTo(BigInteger.valueOf(3));

        assertReason(() -> sell(new BranchVariant(size5.getId(), branchA), operation, 2), Reason.OPERATION_CONFLICT);
        assertReason(() -> sell(atB, UUID.randomUUID(), 1), Reason.INSUFFICIENT_QUANTITY);
        assertReason(() -> sell(new BranchVariant(size5.getId(), branchC), UUID.randomUUID(), 1), Reason.NOT_FOR_SALE);
    }

    @Test
    void aProductNotYetAssignedToABranchSellsAtAnyActiveBranchFromItsTotal() {
        ProductVariant size5 = publishedAtAAndB();
        transaction.executeWithoutResult(status -> jdbc.update(
                "DELETE s FROM catalogue_variant_branch_stock s JOIN catalogue_variants v ON v.id = s.variant_id "
                        + "WHERE v.product_id = ?", size5.getProductId()));

        BranchCartSaleFact fact = sales.readBranchSaleFacts(List.of(new BranchVariant(size5.getId(), branchC))).getFirst();
        assertThat(fact.assignedToBranches()).isFalse();
        assertThat(fact.saleEligible()).isTrue();
        assertThat(fact.availableQuantity()).isEqualTo(BigInteger.valueOf(5));

        BranchAcceptedSaleResult sold = sell(new BranchVariant(size5.getId(), branchC), UUID.randomUUID(), 1);
        assertThat(sold.sizeRemainingAfter()).isEqualTo(BigInteger.valueOf(4));
        assertThat(sold.branchRemainingAfter()).isEqualTo(BigInteger.valueOf(4));
    }

    @Test
    void thePublicProductListsTheSellingBranchesPerSizeAndTheBuyableFilterUsesThem() {
        ProductVariant size5 = publishedAtAAndB();   // size 5: A = 3, B = 2; size 12: A = 0, B = 0
        long size12 = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(size5.getProductId())).get(1).getId();
        branchStock.setSaleEnabled(STAFF, UUID.randomUUID(), size5.getProductId().toString(), Long.toString(branchB), false);

        var product = publicCatalogue.get(size5.getProductId().toString());
        var view5 = product.variants().stream().filter(view -> view.id().equals(size5.getId().toString())).findFirst().orElseThrow();
        var view12 = product.variants().stream().filter(view -> view.id().equals(Long.toString(size12))).findFirst().orElseThrow();

        // B stopped selling: not listed. A sells size 5 (3 left) but size 12 has nothing there.
        assertThat(view5.assignedToBranches()).isTrue();
        assertThat(view5.branches()).extracting(branch -> branch.locationId() + ":" + branch.canBuy())
                .containsExactly(branchA + ":true");
        assertThat(view5.canBuy()).isTrue();
        assertThat(view12.branches()).extracting(branch -> branch.locationId() + ":" + branch.canBuy())
                .containsExactly(branchA + ":false");
        assertThat(view12.canBuy()).isFalse();

        // Sold out at A as well → the product leaves the "can buy" filter.
        branchStock.setSaleEnabled(STAFF, UUID.randomUUID(), size5.getProductId().toString(), Long.toString(branchA), false);
        var buyable = publicCatalogue.list(new edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service
                .PublicCatalogueQueryService.Filter(null, null, null, null, true, null, null));
        assertThat(buyable.items()).noneMatch(item -> item.id().equals(size5.getProductId().toString()));
    }

    // ---- helpers ----

    /** Published product (sizes 5 and 12) sold at A (size 5: 3) and B (size 5: 2); returns size 5. */
    private ProductVariant publishedAtAAndB() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        branchStock.addBranch(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                new ProductBranchStockService.BranchInput(Long.toString(branchB),
                        List.of(new ProductBranchStockService.SizeQuantity(5, "2"))));
        return transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId)).getFirst();
    }

    private BranchAcceptedSaleResult sell(BranchVariant key, UUID operation, int quantity) {
        return transaction.execute(status -> sales.withLockedHistoricalBranchContext("order-" + operation, List.of(key),
                context -> sales.recordAcceptedSale(operation, key.variantId(), key.locationId(), BigInteger.valueOf(quantity))));
    }

    private List<BigInteger> rows(long variantId) {
        return transaction.execute(status -> stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(List.of(variantId)))
                .stream().map(VariantBranchStock::getRemainingUnsold).toList();
    }

    private BigInteger total(long variantId) {
        return transaction.execute(status -> variants.findById(variantId).orElseThrow().getRemainingUnsold());
    }

    private long branch(String code) {
        jdbc.update("INSERT IGNORE INTO workshop_locations (code, display_name, address_snapshot, active) VALUES (?, ?, ?, 1)",
                code, "Chi nhánh " + code, "Địa chỉ");
        jdbc.update("UPDATE workshop_locations SET active = 1 WHERE code = ?", code);
        return jdbc.queryForObject("SELECT id FROM workshop_locations WHERE code = ?", Long.class, code);
    }

    private static void assertReason(Runnable call, Reason reason) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CatalogueSalesException.class,
                error -> assertThat(error.reason()).isEqualTo(reason));
    }

}
