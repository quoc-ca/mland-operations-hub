package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.MediaReferenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaCleanupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantQuantityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.Concurrently;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.RealRetailCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.RealRetailCheckout.Accepted;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.RealRetailHolds;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T062/T065 on MySQL 8.4 with FE-08's REAL checkout, cancellation and payment services ({@link RealRetailCheckout},
 * develop b367505; only the gateway evidence is simulated): the accepted sale is recorded exactly once in FE-08's
 * guarded transaction, an accepted checkout still settles or cancels after its product was hidden or deleted, delete
 * racing a real acceptance never sells a deleted product, a failure in either module rolls both back, and the order's
 * images stay retained (FR-020, FR-023–FR-026, SC-002/004).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({CatalogueTestSupport.MYSQL_PROFILE, RealRetailCheckout.PROFILE})
@Import({CatalogueFlowFixture.class, RealRetailCheckout.class, RealRetailHolds.class})
class CatalogueLifecycleConcurrencyIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private RealRetailCheckout retail;
    @Autowired private RealRetailHolds holds;
    @Autowired private CatalogueSalesFacade sales;
    @Autowired private VariantQuantityService quantities;
    @Autowired private ProductPublicationService publication;
    @Autowired private DeleteProductService deletion;
    @Autowired private CatalogueMediaCleanupService mediaCleanup;
    @Autowired private MediaReferenceRepository references;
    @Autowired private ProductVariantRepository variants;
    @Autowired private LoyaltyFacade loyalty;
    @Autowired private TransactionTemplate transaction;

    @Test
    void aPaidCheckoutRecordsTheSaleOnceAndARepeatedPaymentDeductsNothing() {
        Sale sale = sale(5);
        Accepted accepted = retail.accept(sale.sizeId(), 2);
        assertThat(remaining(sale.sizeId())).isEqualTo(BigInteger.valueOf(5));
        assertThat(holds.held(sale.sizeId())).isEqualTo(BigInteger.TWO);

        String proof = "proof-" + UUID.randomUUID();
        retail.pay(accepted, proof);
        retail.pay(accepted, proof);

        assertThat(retail.paymentState(accepted)).isEqualTo("PAID");
        assertThat(remaining(sale.sizeId())).isEqualTo(BigInteger.valueOf(3));
        assertThat(holds.held(sale.sizeId())).isZero();
        assertThat(available(sale.sizeId())).isEqualTo(BigInteger.valueOf(3));
    }

    @Test
    void checkoutsAcceptedBeforeHidingOrDeletingStillSettleAndCancel() {
        Sale sale = sale(5);
        Accepted toPay = retail.accept(sale.sizeId(), 2);
        Accepted toCancel = retail.accept(sale.sizeId(), 1);

        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(sale.productId()),
                fixture.productVersion(sale.productId()), false);
        retail.pay(toPay, "proof-" + UUID.randomUUID());
        deletion.delete(MANAGER, UUID.randomUUID(), Long.toString(sale.productId()), fixture.productVersion(sale.productId()));
        retail.cancel(toCancel);

        assertThat(retail.paymentState(toPay)).isEqualTo("PAID");
        assertThat(retail.paymentState(toCancel)).isEqualTo("CANCELLED");
        assertThat(remaining(sale.sizeId())).isEqualTo(BigInteger.valueOf(3));
        assertThat(holds.held(sale.sizeId())).isZero();
        // No new sale on a deleted product.
        assertThatThrownBy(() -> retail.accept(sale.sizeId(), 1)).isInstanceOf(RetailConflictException.class);
    }

    @Test
    void deleteRacingARealAcceptanceNeverSellsADeletedProduct() {
        for (int round = 0; round < 3; round++) {
            Sale sale = sale(5);
            long member = retail.newMember();
            long version = fixture.productVersion(sale.productId());

            List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                    () -> deletion.delete(MANAGER, UUID.randomUUID(), Long.toString(sale.productId()), version),
                    () -> retail.accept(member, sale.sizeId(), 1));

            assertThat(outcomes.get(0).succeeded()).isTrue();
            if (outcomes.get(1).succeeded()) {
                // Accepted before the deletion committed: it is still honoured through the historical path.
                Accepted accepted = (Accepted) outcomes.get(1).value();
                retail.pay(accepted, "proof-" + UUID.randomUUID());
                assertThat(remaining(sale.sizeId())).isEqualTo(BigInteger.valueOf(4));
            } else {
                assertThat(outcomes.get(1).failure()).isInstanceOf(RetailConflictException.class);
                assertThat(holds.held(sale.sizeId())).isZero();
                assertThat(remaining(sale.sizeId())).isEqualTo(BigInteger.valueOf(5));
            }
        }
    }

    /**
     * A sale that productmanagement refuses rolls back FE-08's payment and loyalty's settlement with it. The refusal is
     * provoked by using FE-08's operation reference (deterministic per checkout and size) for another quantity first.
     */
    @Test
    void aRefusedSaleRollsBackThePaymentInBothModules() {
        Sale sale = sale(5);
        Accepted accepted = retail.accept(sale.sizeId(), 2);
        UUID fe08Operation = UUID.nameUUIDFromBytes(("retail-paid:" + accepted.checkoutId() + ":" + sale.sizeId())
                .getBytes(StandardCharsets.UTF_8));
        transaction.executeWithoutResult(status -> sales.withLockedHistoricalContext("other-" + accepted.checkoutId(),
                List.of(sale.sizeId()), context -> sales.recordAcceptedSale(fe08Operation, sale.sizeId(), BigInteger.ONE)));
        assertThat(remaining(sale.sizeId())).isEqualTo(BigInteger.valueOf(4));

        assertThatThrownBy(() -> retail.pay(accepted, "proof-" + UUID.randomUUID()))
                .isInstanceOfSatisfying(CatalogueSalesException.class,
                        error -> assertThat(error.reason()).isEqualTo(CatalogueSalesException.Reason.OPERATION_CONFLICT));

        assertThat(retail.paymentState(accepted)).isEqualTo("PAYMENT_INITIATED");
        assertThat(holds.held(sale.sizeId())).as("FE-08 hold not settled").isEqualTo(BigInteger.TWO);
        assertThat(remaining(sale.sizeId())).isEqualTo(BigInteger.valueOf(4));
        assertThat(loyalty.findBenefitResult(SourceKind.RETAIL, accepted.checkoutId()).settledAt())
                .as("loyalty settlement rolled back").isNull();
    }

    @Test
    void theImagesOfAnOrderStayRetainedAfterTheProductIsDeleted() {
        Sale sale = sale(5);
        retail.accept(sale.sizeId(), 1);
        UUID image = fixture.images(sale.productId()).get(0).getMediaObjectId();

        deletion.delete(MANAGER, UUID.randomUUID(), Long.toString(sale.productId()), fixture.productVersion(sale.productId()));

        boolean retained = transaction.execute(status -> references.existsByMediaObjectIdAndReleasedAtIsNull(image));
        assertThat(retained).isTrue();
        assertThat(mediaCleanup.clean(image, Instant.now().plus(Duration.ofDays(60))))
                .isEqualTo(CatalogueMediaCleanupService.Result.SKIPPED);
        assertThat(fixture.media(image).getState()).isEqualTo(CatalogueMediaObject.State.READY);
    }

    // ---- helpers ----

    private record Sale(long productId, long sizeId) {
    }

    private Sale sale(long quantity) {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId)).get(0);
        quantities.setQuantity(STAFF, UUID.randomUUID(), size.getId().toString(), size.getVersion(), Long.toString(quantity));
        return new Sale(productId, size.getId());
    }

    private BigInteger remaining(long sizeId) {
        return transaction.execute(status -> variants.findById(sizeId).orElseThrow().getRemainingUnsold());
    }

    private BigInteger available(long sizeId) {
        return sales.readSaleFacts(List.of(sizeId)).get(0).availableQuantity();
    }
}
