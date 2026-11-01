package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException.Reason;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantQuantityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.Concurrently;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.RealRetailHolds;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantAvailabilityGuardRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailAvailabilityGuardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T042 (and the real-source part of T019/T046/T062) on MySQL 8.4 with the REAL FE-08 hold source
 * ({@link RetailAvailabilityGuardService}, guard rows and holds of {@code readyringsales}, develop b0e7796). No
 * test double: holds are created, released, expired and settled through FE-08's own services in FE-08's order
 * ({@link RealRetailHolds}). The "source unavailable → 503, never zero" case needs a source that can fail on demand
 * and stays in CatalogueSalesIT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import({CatalogueFlowFixture.class, RealRetailHolds.class})
class CatalogueQuantityIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private RealRetailHolds retail;
    @Autowired private CatalogueSalesFacade sales;
    @Autowired private VariantQuantityService quantities;
    @Autowired private ProductVariantRepository variants;
    @Autowired private AvailabilityGuardPort source;
    @Autowired private RetailVariantAvailabilityGuardRepository guards;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate readCommitted;

    @BeforeEach
    void setUp() {
        readCommitted = new TransactionTemplate(transactionManager);
        readCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Test
    void theRuntimeSourceIsTheRealFe08Guard() {
        assertThat(source).isInstanceOf(RetailAvailabilityGuardService.class);
    }

    @Test
    void tenMinusTwoHeldLeavesEightAndOnlyAnExpiredStateStopsCounting() {
        long sizeId = sellableSize(10);
        retail.hold(retail.newCheckout(), sizeId, 2);

        assertThat(available(sizeId)).isEqualTo(BigInteger.valueOf(8));

        // FE-08 owns expiry: a hold keeps counting after its deadline until FE-08 marks it EXPIRED (never
        // inferred from the deadline by productmanagement, so the catalogue never oversells meanwhile).
        String late = retail.newCheckout();
        retail.hold(late, sizeId, 3, Instant.now().plusSeconds(2));
        waitUntil(Instant.now().plusSeconds(3));
        assertThat(available(sizeId)).isEqualTo(BigInteger.valueOf(5));
        retail.expire(late, sizeId);
        assertThat(available(sizeId)).isEqualTo(BigInteger.valueOf(8));
    }

    @Test
    void remainingCannotGoBelowWhatIsHeldAndNothingChanges() {
        long sizeId = sellableSize(10);
        retail.hold(retail.newCheckout(), sizeId, 4);

        ProductVariant size = size(sizeId);
        assertThatThrownBy(() -> quantities.setQuantity(STAFF, UUID.randomUUID(), Long.toString(sizeId), size.getVersion(), "3"))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.QUANTITY_BELOW_HELD));
        assertThat(size(sizeId).getRemainingUnsold()).isEqualTo(BigInteger.TEN);

        quantities.setQuantity(STAFF, UUID.randomUUID(), Long.toString(sizeId), size.getVersion(), "4");
        assertThat(size(sizeId).getRemainingUnsold()).isEqualTo(BigInteger.valueOf(4));
        assertThat(available(sizeId)).isZero();
    }

    @Test
    void aStaleVariantVersionIsRefusedEvenWithTheRealSource() {
        long sizeId = sellableSize(10);
        long staleVersion = size(sizeId).getVersion() - 1;

        assertThatThrownBy(() -> quantities.setQuantity(STAFF, UUID.randomUUID(), Long.toString(sizeId), staleVersion, "9"))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.STALE_VERSION));
        assertThat(size(sizeId).getRemainingUnsold()).isEqualTo(BigInteger.TEN);
    }

    @Test
    void aSizeWithoutAnyHoldGetsAStableGuardRow() {
        long sizeId = sellableSize(10);
        // sellableSize set the quantity under the real guard: the guard row exists although nothing was ever held.
        assertThat(inTx(() -> guards.existsById(sizeId))).isTrue();
        assertThat(retail.held(sizeId)).isZero();
    }

    @Test
    void releasingAHoldGivesTheQuantityBackAndChangesTheSourceRevision() {
        long sizeId = sellableSize(10);
        String checkout = retail.newCheckout();
        retail.hold(checkout, sizeId, 2);
        long revisionBefore = retail.sourceRevision(sizeId);

        retail.release(checkout, sizeId);

        assertThat(available(sizeId)).isEqualTo(BigInteger.TEN);
        assertThat(retail.sourceRevision(sizeId)).isGreaterThan(revisionBefore);
    }

    @Test
    void settlingAHoldAndRecordingTheSaleDeductsExactlyOnce() {
        long sizeId = sellableSize(10);
        String checkout = retail.newCheckout();
        retail.hold(checkout, sizeId, 2);
        UUID operation = UUID.randomUUID();

        retail.settleAndRecord(checkout, sizeId, 2, operation);
        retail.settleAndRecord(checkout, sizeId, 2, operation);

        assertThat(size(sizeId).getRemainingUnsold()).isEqualTo(BigInteger.valueOf(8));
        assertThat(available(sizeId)).isEqualTo(BigInteger.valueOf(8));
        assertThat(retail.holdStatus(checkout, sizeId)).isEqualTo("SETTLED");
    }

    @Test
    void recordingASaleWhileItsHoldIsStillHeldIsRefused() {
        long sizeId = sellableSize(2);
        String checkout = retail.newCheckout();
        retail.hold(checkout, sizeId, 2);

        // Without settling first, 2 − 2 = 0 would fall below the 2 still held.
        assertThatThrownBy(() -> inTx(() -> sales.withLockedHistoricalContext(checkout, List.of(sizeId),
                context -> sales.recordAcceptedSale(UUID.randomUUID(), sizeId, BigInteger.TWO))))
                .isInstanceOfSatisfying(CatalogueSalesException.class,
                        error -> assertThat(error.reason()).isEqualTo(Reason.INSUFFICIENT_QUANTITY));
        assertThat(size(sizeId).getRemainingUnsold()).isEqualTo(BigInteger.TWO);
    }

    @Test
    void aQuantityCutRacingANewHoldNeverLeavesLessThanHeld() {
        for (int round = 0; round < 3; round++) {
            long sizeId = sellableSize(5);
            long version = size(sizeId).getVersion();
            String checkout = retail.newCheckout();

            List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                    () -> retail.hold(checkout, sizeId, 4),
                    () -> quantities.setQuantity(STAFF, UUID.randomUUID(), Long.toString(sizeId), version, "2"));

            assertThat(size(sizeId).getRemainingUnsold()).as("remaining ≥ held, round " + round)
                    .isGreaterThanOrEqualTo(retail.held(sizeId));
            assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        }
    }

    @Test
    void aQuantityCutRacingASettlementKeepsRemainingAboveHeld() {
        for (int round = 0; round < 3; round++) {
            long sizeId = sellableSize(6);
            String sold = retail.newCheckout();
            retail.hold(sold, sizeId, 2);
            retail.hold(retail.newCheckout(), sizeId, 3);
            long version = size(sizeId).getVersion();

            List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                    () -> {
                        retail.settleAndRecord(sold, sizeId, 2, UUID.randomUUID());
                        return null;
                    },
                    () -> quantities.setQuantity(STAFF, UUID.randomUUID(), Long.toString(sizeId), version, "5"));

            // The settlement always succeeds; the cut to 5 succeeds only if it ran first (5 ≥ 5 held), after the
            // settlement the stale version is refused.
            assertThat(outcomes.get(0).succeeded()).isTrue();
            assertThat(retail.held(sizeId)).isEqualTo(BigInteger.valueOf(3));
            assertThat(size(sizeId).getRemainingUnsold()).isGreaterThanOrEqualTo(retail.held(sizeId))
                    .isEqualTo(outcomes.get(1).succeeded() ? BigInteger.valueOf(3) : BigInteger.valueOf(4));
        }
    }

    // ---- helpers ----

    /** A published, sellable size whose remaining quantity was set under the real FE-08 guard. */
    private long sellableSize(long quantity) {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size = inTx(() -> variants.findByProductIdOrderByRingSizeAsc(productId)).get(0);
        quantities.setQuantity(STAFF, UUID.randomUUID(), size.getId().toString(), size.getVersion(), Long.toString(quantity));
        return size.getId();
    }

    private BigInteger available(long sizeId) {
        return sales.readSaleFacts(List.of(sizeId)).get(0).availableQuantity();
    }

    private ProductVariant size(long sizeId) {
        return inTx(() -> variants.findById(sizeId).orElseThrow());
    }

    private <T> T inTx(Supplier<T> work) {
        return readCommitted.execute(status -> work.get());
    }

    private static void waitUntil(Instant moment) {
        while (Instant.now().isBefore(moment)) {
            Thread.onSpinWait();
        }
    }
}
