package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.MediaReferenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AcceptedSaleResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException.Reason;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SaleContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SaleVariantFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueSalesService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantQuantityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantSaleStatusService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.Concurrently;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.TestOnlyAvailabilityGuard;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
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
 * T019/T046/T048/T062 on MySQL, productmanagement side only: {@link CatalogueSalesFacade} and the
 * {@link AvailabilityGuardPort} binding. The FE-08 hold source does not exist yet, so the guard is the
 * TEST-ONLY {@link TestOnlyAvailabilityGuard} (in-memory held quantities, no real guard row). These tests do
 * not prove the FE-08 integration (T003/T042/T058/T065 stay open). Each call runs in its own READ COMMITTED
 * transaction, as the FE-08 caller is expected to.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import({CatalogueFlowFixture.class, CatalogueSalesIT.StubSource.class})
class CatalogueSalesIT {

    @TestConfiguration
    static class StubSource {
        static final TestOnlyAvailabilityGuard SOURCE = new TestOnlyAvailabilityGuard();

        @Bean
        AvailabilityGuardPort testOnlyAvailabilityGuard() {
            return SOURCE;
        }
    }

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private CatalogueSalesFacade sales;
    @Autowired private VariantQuantityService quantities;
    @Autowired private VariantSaleStatusService saleStatus;
    @Autowired private ProductPublicationService publication;
    @Autowired private DeleteProductService deletion;
    @Autowired private ProductVariantRepository variants;
    @Autowired private CatalogueCommandReceiptRepository receipts;
    @Autowired private MediaReferenceRepository references;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate readCommitted;

    @BeforeEach
    void setUp() {
        StubSource.SOURCE.reset();
        readCommitted = new TransactionTemplate(transactionManager);
        readCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    // ---- cart facts (readSaleFacts, FE-08 add-to-cart / cart preview) ----

    @Test
    void cartFactsGiveThePriceAndTheQuantityLeftAfterHolds() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        List<ProductVariant> sizes = sizes(productId);
        StubSource.SOURCE.setHeld(sizes.get(0).getId(), 1);

        // No caller transaction needed; IDs are de-duplicated and returned in ascending order.
        List<CartSaleFact> facts = sales.readSaleFacts(
                List.of(sizes.get(1).getId(), sizes.get(0).getId(), sizes.get(1).getId()));

        assertThat(facts).extracting(CartSaleFact::variantId).containsExactly(sizes.get(0).getId(), sizes.get(1).getId());
        CartSaleFact size5 = facts.get(0);
        assertThat(size5.productId()).isEqualTo(productId);
        assertThat(size5.sku()).isEqualTo(sizes.get(0).getSku());
        assertThat(size5.size()).isEqualTo(5);
        assertThat(size5.appliedPrice()).isEqualTo(new BigInteger("150000"));
        assertThat(size5.currency()).isEqualTo("VND");
        assertThat(size5.saleEligible()).isTrue();
        assertThat(size5.availableQuantity()).isEqualTo(BigInteger.TWO);
        assertThat(facts.get(1).saleEligible()).isTrue();
        assertThat(facts.get(1).availableQuantity()).isZero();
        assertThat(StubSource.SOURCE.guardCalls()).isEmpty();

        // Held above remaining (not expected, but never negative).
        StubSource.SOURCE.setHeld(sizes.get(0).getId(), 5);
        assertThat(sales.readSaleFacts(List.of(sizes.get(0).getId())).get(0).availableQuantity()).isZero();
    }

    @Test
    void cartFactsMarkSizesNotForSaleInsteadOfRejecting() {
        long hidden = fixture.publishedProduct(STAFF, MANAGER);
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(hidden), fixture.productVersion(hidden), false);
        long disabled = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant disabledSize = sizes(disabled).get(0);
        saleStatus.setSaleEnabled(STAFF, UUID.randomUUID(), disabledSize.getId().toString(), disabledSize.getVersion(), false);
        long deleted = fixture.publishedProduct(STAFF, MANAGER);
        deletion.delete(STAFF, UUID.randomUUID(), Long.toString(deleted), fixture.productVersion(deleted));
        long draft = fixture.draftProduct(STAFF);

        List<Long> refused = List.of(sizes(hidden).get(0).getId(), disabledSize.getId(), sizes(deleted).get(0).getId(),
                sizes(draft).get(0).getId());
        List<CartSaleFact> facts = sales.readSaleFacts(refused);

        assertThat(facts).hasSize(4).allSatisfy(fact -> {
            assertThat(fact.saleEligible()).isFalse();
            assertThat(fact.availableQuantity()).isZero();
        });
        assertThat(sales.readSaleFacts(List.of(sizes(disabled).get(1).getId())).get(0).saleEligible()).isTrue();
    }

    @Test
    void cartFactsRejectBadRequestsAndAMissingSource() {
        long sizeId = sizes(fixture.publishedProduct(STAFF, MANAGER)).get(0).getId();

        assertSales(() -> sales.readSaleFacts(List.of()), Reason.INVALID_REQUEST);
        assertSales(() -> sales.readSaleFacts(null), Reason.INVALID_REQUEST);
        assertSales(() -> sales.readSaleFacts(List.of(sizeId, Long.MAX_VALUE)), Reason.NOT_FOUND, Long.MAX_VALUE);
        StubSource.SOURCE.setUnavailable(true);
        assertSales(() -> sales.readSaleFacts(List.of(sizeId)), Reason.DEPENDENCY_UNAVAILABLE);
        // Inside a caller transaction it joins it.
        StubSource.SOURCE.setUnavailable(false);
        assertThat(inTx(() -> sales.readSaleFacts(List.of(sizeId)))).hasSize(1);
    }

    // ---- sale context ----

    @Test
    void saleContextReturnsImmutableFactsOfSellableSizes() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        List<ProductVariant> sizes = sizes(productId);

        SaleContext context = inTx(() -> sales.withLockedSaleContext(
                List.of(sizes.get(1).getId(), sizes.get(0).getId()), c -> c));

        assertThat(context.historical()).isFalse();
        assertThat(context.variants()).extracting(SaleVariantFact::variantId)
                .containsExactly(sizes.get(0).getId(), sizes.get(1).getId());
        SaleVariantFact size5 = context.variants().get(0);
        assertThat(size5.productId()).isEqualTo(productId);
        assertThat(size5.size()).isEqualTo(5);
        assertThat(size5.sku()).isEqualTo(sizes.get(0).getSku());
        assertThat(size5.appliedPrice()).isEqualTo(new BigInteger("150000"));
        assertThat(size5.currency()).isEqualTo("VND");
        assertThat(size5.remainingUnsold()).isEqualTo(BigInteger.valueOf(3));
        assertThat(size5.publiclyEligible()).isTrue();
        assertThat(size5.imageMediaIds()).containsExactly(fixture.images(productId).get(0).getMediaObjectId());
        assertThat(context.variants().get(1).remainingUnsold()).isZero();
    }

    @Test
    void newSaleIsRefusedForSizesNotOpenForSale() {
        long draft = fixture.draftProduct(STAFF);
        long hidden = fixture.publishedProduct(STAFF, MANAGER);
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(hidden), fixture.productVersion(hidden), false);
        long disabled = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant disabledSize = sizes(disabled).get(0);
        saleStatus.setSaleEnabled(STAFF, UUID.randomUUID(), disabledSize.getId().toString(), disabledSize.getVersion(), false);
        long deleted = fixture.publishedProduct(STAFF, MANAGER);
        deletion.delete(STAFF, UUID.randomUUID(), Long.toString(deleted), fixture.productVersion(deleted));
        long open = fixture.publishedProduct(STAFF, MANAGER);

        for (long refusedSize : List.of(sizes(draft).get(0).getId(), sizes(hidden).get(0).getId(),
                disabledSize.getId(), sizes(deleted).get(0).getId())) {
            long openSize = sizes(open).get(0).getId();
            assertSales(() -> inTx(() -> sales.withLockedSaleContext(List.of(openSize, refusedSize), c -> c)),
                    Reason.NOT_FOR_SALE, refusedSize);
        }
        // The other size of the disabled product is still for sale.
        assertThat(inTx(() -> sales.withLockedSaleContext(List.of(sizes(disabled).get(1).getId()), c -> c))
                .variants()).hasSize(1);
        assertSales(() -> inTx(() -> sales.withLockedSaleContext(List.of(Long.MAX_VALUE), c -> c)), Reason.NOT_FOUND);
        assertSales(() -> inTx(() -> sales.withLockedSaleContext(List.of(), c -> c)), Reason.INVALID_REQUEST);
    }

    @Test
    void historicalContextAllowsHiddenAndDeletedProducts() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        deletion.delete(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId));
        long sizeId = sizes(productId).get(0).getId();

        SaleContext context = inTx(() -> sales.withLockedHistoricalContext("order-1", List.of(sizeId), c -> c));

        assertThat(context.historical()).isTrue();
        assertThat(context.acceptedTransactionRef()).isEqualTo("order-1");
        assertThat(context.variants().get(0).productDeleted()).isTrue();
        assertThat(context.variants().get(0).publiclyEligible()).isFalse();
        assertSales(() -> inTx(() -> sales.withLockedHistoricalContext(" ", List.of(sizeId), c -> c)),
                Reason.INVALID_REQUEST);
    }

    @Test
    void facadeRequiresTheCallersTransaction() {
        long sizeId = sizes(fixture.publishedProduct(STAFF, MANAGER)).get(0).getId();
        assertThatThrownBy(() -> sales.withLockedSaleContext(List.of(sizeId), c -> c))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void callbackExceptionsPassThroughUnchanged() {
        long sizeId = sizes(fixture.publishedProduct(STAFF, MANAGER)).get(0).getId();
        IllegalArgumentException own = new IllegalArgumentException("FE-08 rejection");
        assertThatThrownBy(() -> inTx(() -> sales.withLockedSaleContext(List.of(sizeId), c -> {
            throw own;
        }))).isSameAs(own);
    }

    // ---- accepted sale ----

    @Test
    void acceptedSaleIsDeductedExactlyOncePerOperation() {
        long sizeId = sizes(fixture.publishedProduct(STAFF, MANAGER)).get(0).getId();
        UUID operation = UUID.randomUUID();

        AcceptedSaleResult first = recordInHistoricalContext(operation, sizeId, 2);
        AcceptedSaleResult again = recordInHistoricalContext(operation, sizeId, 2);

        assertThat(first.replayed()).isFalse();
        assertThat(first.remainingAfter()).isEqualTo(BigInteger.ONE);
        assertThat(again.replayed()).isTrue();
        assertThat(again.remainingAfter()).isEqualTo(BigInteger.ONE);
        assertThat(remaining(sizeId)).isEqualTo(BigInteger.ONE);
        assertThat(receipts.findByActorIdAndActionAndCommandId(CatalogueSalesService.CONSUMER,
                CatalogueSalesService.SALE_ACTION, operation)).isPresent();
        assertSales(() -> recordInHistoricalContext(operation, sizeId, 1), Reason.OPERATION_CONFLICT);
        assertThat(remaining(sizeId)).isEqualTo(BigInteger.ONE);
    }

    @Test
    void acceptedSaleCannotGoBelowZeroOrBelowHeldAfterSettlement() {
        long sizeId = sizes(fixture.publishedProduct(STAFF, MANAGER)).get(0).getId();

        assertSales(() -> recordInHistoricalContext(UUID.randomUUID(), sizeId, 4), Reason.INSUFFICIENT_QUANTITY);
        StubSource.SOURCE.setHeld(sizeId, 2);
        // 3 − 2 = 1 would be below the 2 still held by other valid holds.
        assertSales(() -> recordInHistoricalContext(UUID.randomUUID(), sizeId, 2), Reason.INSUFFICIENT_QUANTITY);
        assertThat(recordInHistoricalContext(UUID.randomUUID(), sizeId, 1).remainingAfter()).isEqualTo(BigInteger.TWO);
        StubSource.SOURCE.setUnavailable(true);
        assertSales(() -> recordInHistoricalContext(UUID.randomUUID(), sizeId, 1), Reason.DEPENDENCY_UNAVAILABLE);
        assertThat(remaining(sizeId)).isEqualTo(BigInteger.TWO);
    }

    @Test
    void acceptedSaleNeedsALockedContextAndValidArguments() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        long size5 = sizes(productId).get(0).getId();
        long size12 = sizes(productId).get(1).getId();

        assertSales(() -> inTx(() -> sales.recordAcceptedSale(UUID.randomUUID(), size5, BigInteger.ONE)),
                Reason.NO_LOCKED_CONTEXT);
        assertSales(() -> inTx(() -> sales.withLockedSaleContext(List.of(size12),
                c -> sales.recordAcceptedSale(UUID.randomUUID(), size5, BigInteger.ONE))), Reason.NO_LOCKED_CONTEXT);
        assertSales(() -> recordInHistoricalContext(UUID.randomUUID(), size5, 0), Reason.INVALID_REQUEST);
        assertThat(remaining(size5)).isEqualTo(BigInteger.valueOf(3));
    }

    @Test
    void concurrentSalesOfTheLastUnitsAreSerialized() {
        long sizeId = sizes(fixture.publishedProduct(STAFF, MANAGER)).get(0).getId();

        List<Concurrently.Outcome<AcceptedSaleResult>> outcomes = Concurrently.run(
                () -> recordInSaleContext(UUID.randomUUID(), sizeId, 2),
                () -> recordInSaleContext(UUID.randomUUID(), sizeId, 2));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement()
                .satisfies(outcome -> assertThat(outcome.failure()).isInstanceOfSatisfying(CatalogueSalesException.class,
                        error -> assertThat(error.reason()).isEqualTo(Reason.INSUFFICIENT_QUANTITY)));
        assertThat(remaining(sizeId)).isEqualTo(BigInteger.ONE);
    }

    // ---- media references ----

    @Test
    void retainedImagesStayReferencedUntilReleased() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        UUID image = fixture.images(productId).get(0).getMediaObjectId();

        inTx(() -> {
            sales.retainMediaReferences("order-item:42", List.of(image));
            return null;
        });
        assertThat(reference(image).isActive()).isTrue();

        inTx(() -> {
            sales.releaseMediaReferences("order-item:42", List.of(image));
            return null;
        });
        assertThat(reference(image).isActive()).isFalse();
        // The product's own reference is untouched.
        assertThat(fixture.media(image).getState()).isEqualTo(CatalogueMediaObject.State.READY);
    }

    @Test
    void mediaThatCannotBeRetainedIsRefused() {
        UUID staged = UUID.randomUUID();
        assertSales(() -> inTx(() -> {
            sales.retainMediaReferences("order-item:43", List.of(staged));
            return null;
        }), Reason.MEDIA_NOT_AVAILABLE);
        assertSales(() -> inTx(() -> {
            sales.retainMediaReferences(" ", List.of());
            return null;
        }), Reason.INVALID_REQUEST);
    }

    // ---- quantity under the source guard (T046) ----

    @Test
    void quantityOfAPublishedSizeIsSetUnderTheSourceGuard() {
        ProductVariant size = sizes(fixture.publishedProduct(STAFF, MANAGER)).get(0);
        StubSource.SOURCE.setHeld(size.getId(), 2);

        assertThatThrownBy(() -> quantities.setQuantity(STAFF, UUID.randomUUID(), size.getId().toString(),
                size.getVersion(), "1")).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.QUANTITY_BELOW_HELD));
        quantities.setQuantity(STAFF, UUID.randomUUID(), size.getId().toString(), size.getVersion(), "2");

        assertThat(remaining(size.getId())).isEqualTo(BigInteger.TWO);
        assertThat(StubSource.SOURCE.guardCalls()).containsExactly(List.of(size.getId()), List.of(size.getId()));
    }

    @Test
    void quantityFailsSafelyWhenTheSourceIsUnavailable() {
        ProductVariant size = sizes(fixture.publishedProduct(STAFF, MANAGER)).get(0);
        StubSource.SOURCE.setUnavailable(true);

        assertThatThrownBy(() -> quantities.setQuantity(STAFF, UUID.randomUUID(), size.getId().toString(),
                size.getVersion(), "10")).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE));
        assertThat(remaining(size.getId())).isEqualTo(BigInteger.valueOf(3));
    }

    // ---- helpers ----

    private AcceptedSaleResult recordInHistoricalContext(UUID operation, long sizeId, long quantity) {
        return inTx(() -> sales.withLockedHistoricalContext("order-" + operation, List.of(sizeId),
                c -> sales.recordAcceptedSale(operation, sizeId, BigInteger.valueOf(quantity))));
    }

    private AcceptedSaleResult recordInSaleContext(UUID operation, long sizeId, long quantity) {
        return inTx(() -> sales.withLockedSaleContext(List.of(sizeId),
                c -> sales.recordAcceptedSale(operation, sizeId, BigInteger.valueOf(quantity))));
    }

    private <T> T inTx(Supplier<T> work) {
        return readCommitted.execute(status -> work.get());
    }

    private List<ProductVariant> sizes(long productId) {
        return readCommitted.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId));
    }

    private BigInteger remaining(long sizeId) {
        return readCommitted.execute(status -> variants.findById(sizeId).orElseThrow().getRemainingUnsold());
    }

    private edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.MediaReference reference(UUID image) {
        return readCommitted.execute(status -> references.findByMediaObjectIdAndOwnerModuleAndOwnerRecordRef(
                image, CatalogueSalesService.CONSUMER, "order-item:42").orElseThrow());
    }

    private static void assertSales(ThrowableAssert.ThrowingCallable action, Reason reason, long... variantIds) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogueSalesException.class, error -> {
            assertThat(error.reason()).isEqualTo(reason);
            for (long id : variantIds) {
                assertThat(error.variantIds()).contains(id);
            }
        });
    }
}
