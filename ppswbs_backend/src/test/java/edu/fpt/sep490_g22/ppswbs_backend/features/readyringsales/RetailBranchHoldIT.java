package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.VariantBranchStockRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchCartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductBranchStockService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.RealRetailHolds;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHoldStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailVariantHoldLifecycleService;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T124 / FR-053 on MySQL 8.4 with the real FE-08 hold source: a hold, its settlement and the accepted sale belong to
 * one size x branch, so holding or selling at branch A never changes what branch B can sell.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import({CatalogueFlowFixture.class, RealRetailHolds.class})
class RetailBranchHoldIT {

    private static final String STAFF = CatalogueTestSupport.STAFF;
    private static final String MANAGER = CatalogueTestSupport.MANAGER;

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private RealRetailHolds retail;
    @Autowired private CatalogueSalesFacade sales;
    @Autowired private AvailabilityGuardPort source;
    @Autowired private ProductBranchStockService branchStock;
    @Autowired private ProductVariantRepository variants;
    @Autowired private VariantBranchStockRepository stocks;
    @Autowired private RetailVariantHoldLifecycleService lifecycle;
    @Autowired private RetailVariantHoldRepository holds;
    @Autowired private GroupedCheckoutRepository checkouts;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;

    private long branchA;
    private long branchB;

    @BeforeEach
    void branches() {
        branchA = fixture.testBranch();
        branchB = branch("FE08-T124-BRANCH-B");
    }

    @Test
    void theFe08SourceTracksHoldsPerBranch() {
        assertThat(source.tracksHoldsPerBranch()).isTrue();
    }

    @Test
    void aHoldAtOneBranchReducesOnlyThatBranch() {
        long size5 = publishedAtAAndB();   // A = 3, B = 2
        String checkout = retail.newCheckout();

        hold(checkout, new BranchVariant(size5, branchA), 2);

        List<BranchCartSaleFact> facts = sales.readBranchSaleFacts(List.of(
            new BranchVariant(size5, branchA), new BranchVariant(size5, branchB)));
        assertThat(facts).extracting(BranchCartSaleFact::availableQuantity)
            .containsExactly(BigInteger.ONE, BigInteger.TWO);
        assertThat(retail.held(size5)).isEqualTo(BigInteger.TWO);
    }

    @Test
    void oneCheckoutMayHoldTheSameSizeAtTwoBranches() {
        long size5 = publishedAtAAndB();
        String checkout = retail.newCheckout();

        hold(checkout, new BranchVariant(size5, branchA), 1);
        hold(checkout, new BranchVariant(size5, branchB), 2);

        List<RetailVariantHold> created = transaction.execute(status -> holds.findByGroupedCheckoutId(checkout));
        assertThat(created).hasSize(2);
        assertThat(retail.held(size5)).isEqualTo(BigInteger.valueOf(3));
    }

    @Test
    void settlingAtABranchSellsFromThatBranchOnly() {
        long size5 = publishedAtAAndB();
        String checkout = retail.newCheckout();
        BranchVariant atA = new BranchVariant(size5, branchA);
        hold(checkout, atA, 2);

        transaction.executeWithoutResult(status -> sales.withLockedHistoricalBranchContext(checkout, List.of(atA), context -> {
            source.withBranchGuard(List.of(atA), context.asOf(), snapshots -> {
                lifecycle.settle(lifecycle.requireBranchHolds(checkout, List.of(atA)).getFirst());
                return null;
            });
            return sales.recordAcceptedSale(UUID.randomUUID(), size5, branchA, BigInteger.TWO);
        }));

        RetailVariantHold settled = transaction.execute(status -> holds
            .findByGroupedCheckoutIdAndVariantIdAndLocationId(checkout, size5, branchA).orElseThrow());
        assertThat(settled.getStatus()).isEqualTo(RetailVariantHoldStatus.SETTLED);
        assertThat(rows(size5)).containsExactly(BigInteger.ONE, BigInteger.TWO);
        assertThat(retail.held(size5)).isZero();
    }

    private void hold(String checkoutId, BranchVariant key, int quantity) {
        transaction.executeWithoutResult(status -> sales.withLockedBranchSaleContext(List.of(key), context ->
            source.withBranchGuard(List.of(key), context.asOf(), snapshots -> lifecycle.createHeld(
                checkouts.findById(checkoutId).orElseThrow(), key.variantId(), key.locationId(), quantity,
                Instant.now().plus(15, ChronoUnit.MINUTES)))));
    }

    /** Published product sold at A (size 5: 3) and B (size 5: 2); returns size 5. */
    private long publishedAtAAndB() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        branchStock.addBranch(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
            new ProductBranchStockService.BranchInput(Long.toString(branchB),
                List.of(new ProductBranchStockService.SizeQuantity(5, "2"))));
        return transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId)).getFirst().getId();
    }

    private List<BigInteger> rows(long variantId) {
        return transaction.execute(status -> stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(List.of(variantId)))
            .stream().map(VariantBranchStock::getRemainingUnsold).toList();
    }

    private long branch(String code) {
        jdbc.update("INSERT IGNORE INTO workshop_locations (code, display_name, address_snapshot, active) VALUES (?, ?, ?, 1)",
            code, "Chi nhánh " + code, "Địa chỉ");
        jdbc.update("UPDATE workshop_locations SET active = 1 WHERE code = ?", code);
        return jdbc.queryForObject("SELECT id FROM workshop_locations WHERE code = ?", Long.class, code);
    }
}
