package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailVariantHoldLifecycleService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutQuotePreviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutAcceptanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCheckoutQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCheckoutQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCheckoutQuoteBranch;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCheckoutQuoteItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.UUID;
import java.util.List;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class RetailCatalogueMySqlIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired CatalogueFlowFixture fixture;
    @Autowired ProductVariantRepository variants;
    @Autowired AccountRepository accounts;
    @Autowired CartService cart;
    @Autowired CatalogueSalesFacade sales;
    @Autowired AvailabilityGuardPort guard;
    @Autowired TransactionTemplate transaction;
    @Autowired GroupedCheckoutRepository checkouts;
    @Autowired RetailVariantHoldLifecycleService holds;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired RetailCheckoutQuoteRepository quotes;
    @Autowired RetailCartRepository carts;
    @Autowired RetailCheckoutQuotePreviewService quotePreview;
    @Autowired RetailCheckoutAcceptanceService acceptance;
    @Autowired JdbcTemplate jdbc;

    @Test
    void twoMembersCannotBothAcceptTheLastStock() throws Exception {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long firstMember = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-race1-" + random)
            .loginEmail("fe08-race1-" + random + "@example.test").build()).getId();
        long secondMember = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-race2-" + random)
            .loginEmail("fe08-race2-" + random + "@example.test").build()).getId();
        cart.addLine(firstMember, new CartDtos.AddCartLineRequest(variant, 1L, 3));
        cart.addLine(secondMember, new CartDtos.AddCartLineRequest(variant, 1L, 3));
        var selection = new CheckoutDtos.BenefitSelection("NONE", null, "0");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var firstQuote = quotePreview.preview(firstMember, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        var secondQuote = quotePreview.preview(secondMember, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        var firstCommand = new CheckoutDtos.CreateGroupedCheckoutRequest(choices, firstQuote.previewId(), selection, true);
        var secondCommand = new CheckoutDtos.CreateGroupedCheckoutRequest(choices, secondQuote.previewId(), selection, true);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> attemptCheckout(firstMember, "checkout-race-a-" + random, firstCommand, start));
            var b = executor.submit(() -> attemptCheckout(secondMember, "checkout-race-b-" + random, secondCommand, start));
            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        }
        BigInteger held = transaction.execute(status -> guard.readSnapshots(List.of(variant), Instant.now())
            .get(variant).heldQuantity());
        assertThat(held).isEqualTo(BigInteger.valueOf(3));
    }

    private boolean attemptCheckout(long member, String key, CheckoutDtos.CreateGroupedCheckoutRequest command,
                                    CountDownLatch start) throws InterruptedException {
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Checkout race did not start");
        try {
            acceptance.accept(member, key, command);
            return true;
        } catch (RetailConflictException exception) {
            return false;
        }
    }

    @Test
    void changedLoyaltyDigestRollsBackOrderHoldAndCartClear() {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-rollback-" + random)
            .loginEmail("fe08-rollback-" + random + "@example.test").build()).getId();
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var selection = new CheckoutDtos.BenefitSelection("NONE", null, "0");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        jdbc.update("update retail_checkout_quotes set terms_digest = ? where id = ?", "changed-terms", preview.previewId());
        var command = new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true);

        assertThatThrownBy(() -> acceptance.accept(member, "rollback-" + random, command))
            .isInstanceOf(RetailConflictException.class);
        assertThat(checkouts.findByMemberAccountIdAndIdempotencyKey(member, "rollback-" + random)).isEmpty();
        assertThat(cart.getCart(member).lines()).hasSize(1);
        BigInteger held = transaction.execute(status -> guard.readSnapshots(List.of(variant), Instant.now())
            .get(variant).heldQuantity());
        assertThat(held).isEqualTo(BigInteger.ZERO);
        String state = transaction.execute(status -> quotes.lockOwned(preview.previewId(), member).orElseThrow().getStatus());
        assertThat(state).isEqualTo("OPEN");
    }

    @Test
    void pickupAcceptanceCreatesOneOrderAndHoldAndReplaysSameCommand() {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-accept-" + random)
            .loginEmail("fe08-accept-" + random + "@example.test").build()).getId();
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var selection = new CheckoutDtos.BenefitSelection("NONE", null, "0");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        var command = new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true);

        var accepted = acceptance.accept(member, "accept-" + random, command);
        var replay = acceptance.accept(member, "accept-" + random, command);

        assertThat(replay.id()).isEqualTo(accepted.id());
        assertThat(accepted.branchOrders()).hasSize(1);
        assertThat(accepted.combinedPayableAmount()).isEqualTo("150000");
        assertThat(cart.getCart(member).lines()).isEmpty();
        BigInteger held = transaction.execute(status -> guard.readSnapshots(List.of(variant), Instant.now())
            .get(variant).heldQuantity());
        String quoteStatus = transaction.execute(status -> quotes.lockOwned(preview.previewId(), member).orElseThrow().getStatus());
        assertThat(held).isEqualTo(BigInteger.ONE);
        assertThat(quoteStatus).isEqualTo("ACCEPTED");
    }

    @Test
    void changedCartRejectsAcceptanceWithoutCreatingOrderOrHold() {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-stale-" + random)
            .loginEmail("fe08-stale-" + random + "@example.test").build()).getId();
        var initial = cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var selection = new CheckoutDtos.BenefitSelection("NONE", null, "0");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        cart.updateLine(member, initial.lines().getFirst().id(), new CartDtos.UpdateCartLineRequest(2));
        var command = new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true);

        assertThatThrownBy(() -> acceptance.accept(member, "stale-" + random, command))
            .isInstanceOf(RetailConflictException.class);
        assertThat(checkouts.findByMemberAccountIdAndIdempotencyKey(member, "stale-" + random)).isEmpty();
        BigInteger held = transaction.execute(status -> guard.readSnapshots(List.of(variant), Instant.now())
            .get(variant).heldQuantity());
        assertThat(held).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void pickupPreviewPersistsRealCatalogueAndLoyaltyFacts() {
        CatalogueTestSupport.requireMySql84(dataSource);
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-preview-" + random)
            .loginEmail("fe08-preview-" + random + "@example.test").build()).getId();
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var result = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(
            new CheckoutDtos.BenefitSelection("NONE", null, "0"),
            List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null))));

        assertThat(result.merchandiseSubtotal()).isEqualTo("150000");
        assertThat(result.shippingTotal()).isEqualTo("0");
        assertThat(result.payableAmount()).isEqualTo("150000");
        transaction.executeWithoutResult(status -> {
            var stored = quotes.lockOwned(result.previewId(), member).orElseThrow();
            assertThat(stored.getTermsDigest()).isNotBlank();
            assertThat(stored.getExpiresAt()).isEqualTo(stored.getCreatedAt().plusSeconds(900));
            assertThat(stored.getBranches()).singleElement().satisfies(branch ->
                assertThat(branch.getItems()).singleElement().satisfies(item ->
                    assertThat(item.getVariantId()).isEqualTo(variant)));
        });
    }

    @Test
    void persistsQuoteSnapshotsAndLocksByOwner() {
        CatalogueTestSupport.requireMySql84(dataSource);
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-quote-" + random)
            .loginEmail("fe08-quote-" + random + "@example.test").build()).getId();
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        String quoteId = transaction.execute(status -> {
            var source = carts.findByMemberAccountId(member).orElseThrow();
            Instant now = Instant.now();
            var quote = new RetailCheckoutQuote(member, source.getId(), source.getVersion(), "NONE", null,
                BigInteger.ZERO, "test-digest", "VND", BigInteger.valueOf(150000), BigInteger.ZERO,
                BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(150000), now, now.plusSeconds(900));
            var branch = new RetailCheckoutQuoteBranch(quote, 1L, "PICKUP", null, BigInteger.ZERO, BigInteger.valueOf(150000));
            branch.addItem(new RetailCheckoutQuoteItem(branch, product, variant, 1, BigInteger.valueOf(150000)));
            quote.addBranch(branch);
            return quotes.saveAndFlush(quote).getId();
        });
        transaction.executeWithoutResult(status -> {
            var quote = quotes.lockOwned(quoteId, member).orElseThrow();
            assertThat(quote.getBranches()).singleElement().satisfies(branch ->
                assertThat(branch.getItems()).singleElement().satisfies(item ->
                    assertThat(item.getLineTotal()).isEqualTo(BigInteger.valueOf(150000))));
            assertThat(quotes.lockOwned(quoteId, member + 1)).isEmpty();
        });
    }

    @Test
    void concurrentGuardedReservationsCannotBothHoldTheSameStock() throws Exception {
        CatalogueTestSupport.requireMySql84(dataSource);
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String id = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-race-" + id)
            .loginEmail("fe08-race-" + id + "@example.test").build()).getId();
        String first = checkouts.saveAndFlush(new GroupedCheckout(member, "race-a-" + id, 0,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)).getId();
        String second = checkouts.saveAndFlush(new GroupedCheckout(member, "race-b-" + id, 0,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)).getId();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> tryReserveAll(variant, first, start));
            var b = executor.submit(() -> tryReserveAll(variant, second, start));
            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        }
        BigInteger held = transaction.execute(status -> guard.readSnapshots(List.of(variant), Instant.now())
            .get(variant).heldQuantity());
        assertThat(held).isEqualTo(BigInteger.valueOf(3));
    }

    private boolean tryReserveAll(long variant, String checkoutId, CountDownLatch start) throws Exception {
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
        TransactionTemplate readCommitted = new TransactionTemplate(transactionManager);
        readCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return Boolean.TRUE.equals(readCommitted.execute(status -> sales.withLockedSaleContext(List.of(variant), context ->
            guard.withGuard(List.of(variant), context.asOf(), snapshots -> {
                BigInteger available = context.variants().getFirst().remainingUnsold()
                    .subtract(snapshots.get(variant).heldQuantity());
                if (available.compareTo(BigInteger.valueOf(3)) < 0) return false;
                holds.createHeld(checkouts.findById(checkoutId).orElseThrow(), variant, 3, Instant.now().plusSeconds(900));
                return true;
            }))));
    }

    @Test
    void cartUsesRealCatalogueAndAvailabilitySource() {
        CatalogueTestSupport.requireMySql84(dataSource);
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String id = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-" + id)
            .loginEmail("fe08-" + id + "@example.test").build()).getId();

        var line = cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 2)).lines().getFirst();

        assertThat(line.indicativePrice()).isEqualTo("150000");
        assertThat(line.availableQuantity()).isEqualTo("3");
        assertThat(line.saleEligible()).isTrue();
        assertThatThrownBy(() -> cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 2L, 2)))
            .isInstanceOf(RetailConflictException.class);

        var held = transaction.execute(status -> sales.withLockedSaleContext(List.of(variant), context ->
            guard.withGuard(List.of(variant), context.asOf(), snapshots -> snapshots.get(variant).heldQuantity())));
        assertThat(held).isEqualTo(BigInteger.ZERO);

        transaction.executeWithoutResult(status -> {
            var checkout = checkouts.saveAndFlush(new GroupedCheckout(member, "expired-" + id, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            sales.withLockedSaleContext(List.of(variant), context -> guard.withGuard(List.of(variant), context.asOf(), snapshots -> {
                holds.createHeld(checkout, variant, 1, Instant.now().minusSeconds(60));
                return null;
            }));
        });
        var heldAfterDeadline = transaction.execute(status ->
            guard.readSnapshots(List.of(variant), Instant.now()).get(variant).heldQuantity());
        assertThat(heldAfterDeadline).isEqualTo(BigInteger.ONE);
    }
}
