package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.service.PickupOperationsService;
import org.springframework.web.server.ResponseStatusException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
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
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailOrderQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.SimulatedRetailPaymentOutcomeService;
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
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({CatalogueTestSupport.MYSQL_PROFILE, "retail-payment-sim"})
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
    @Autowired RetailCheckoutCancellationService cancellation;
    @Autowired RetailOrderQueryService orderQueries;
    @Autowired SimulatedRetailPaymentOutcomeService simulatedPayments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PromotionProgramRepository programs;
    @Autowired PromotionRevisionRepository revisions;
    @Autowired PromotionTargetRepository targets;
    @Autowired LoyaltyWalletRepository wallets;
    @Autowired AcceptedBenefitRepository benefits;
    @Autowired PickupOperationsService pickupOperations;

    @Test
    void pickupRequiresPaidOrderAndAssignedStaffAndRecordsEachMilestoneOnce() throws Exception {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-pickup-member-" + random)
            .loginEmail("fe08-pickup-member-" + random + "@example.test").build()).getId();
        long staffId = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-pickup-staff-" + random)
            .loginEmail("fe08-pickup-staff-" + random + "@example.test").build()).getId();
        var staff = new CurrentAccountActor(staffId, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));
        var otherBranch = new CurrentAccountActor(staffId, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(2L));
        var suspended = new CurrentAccountActor(staffId, ActorRole.STAFF, ActorStatus.SUSPENDED, true, List.of(1L));
        var customer = new CurrentAccountActor(member, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of(1L));
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var selection = new CheckoutDtos.BenefitSelection("NONE", null, "0");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        var accepted = acceptance.accept(member, "pickup-" + random,
            new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true));
        String orderId = accepted.branchOrders().getFirst().id();
        assertThatThrownBy(() -> pickupOperations.prepare(staff, orderId)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> pickupOperations.prepare(otherBranch, orderId)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> pickupOperations.prepare(suspended, orderId)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> pickupOperations.prepare(customer, orderId)).isInstanceOf(ResponseStatusException.class);
        transaction.executeWithoutResult(status -> checkouts.lockOwned(accepted.id(), member).orElseThrow().markPaid());
        assertThatThrownBy(() -> pickupOperations.handOver(staff, orderId)).isInstanceOf(ResponseStatusException.class);
        var start = new CountDownLatch(1);
        PickupOperationsService.Result prepared;
        PickupOperationsService.Result repeated;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return pickupOperations.prepare(staff, orderId); });
            var second = executor.submit(() -> { start.await(); return pickupOperations.prepare(staff, orderId); });
            start.countDown();
            prepared = first.get(30, TimeUnit.SECONDS);
            repeated = second.get(30, TimeUnit.SECONDS);
        }
        assertThat(prepared.fulfilmentState()).isEqualTo("READY_FOR_PICKUP");
        assertThat(repeated.recordedAt()).isEqualTo(prepared.recordedAt());
        assertThat(orderQueries.find(member, orderId).fulfilmentState()).isEqualTo("READY_FOR_PICKUP");
        var handedOver = pickupOperations.handOver(staff, orderId);
        assertThat(handedOver.fulfilmentState()).isEqualTo("PICKED_UP");
        assertThat(pickupOperations.handOver(staff, orderId).recordedAt()).isEqualTo(handedOver.recordedAt());
        assertThat(orderQueries.find(member, orderId).fulfilmentState()).isEqualTo("PICKED_UP");
        assertThat(jdbc.queryForObject("select count(*) from retail_pickup_evidence where retail_order_id = ?", Integer.class, orderId)).isEqualTo(2);
    }

    @Test
    void orderHistoryReturnsOnlyOwnersSavedItemsAndAmounts() {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-history-" + random)
            .loginEmail("fe08-history-" + random + "@example.test").build()).getId();
        long other = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-history-other-" + random)
            .loginEmail("fe08-history-other-" + random + "@example.test").build()).getId();
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 2));
        var selection = new CheckoutDtos.BenefitSelection("NONE", null, "0");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        var accepted = acceptance.accept(member, "history-" + random,
            new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true));
        String orderId = accepted.branchOrders().getFirst().id();
        jdbc.update("update catalogue_products set name = ? where id = ?", "New catalogue name", product);
        assertThat(orderQueries.list(member)).singleElement().satisfies(order -> {
            assertThat(order.id()).isEqualTo(orderId);
            assertThat(order.fulfilmentState()).isNull();
            assertThat(order.items()).singleElement().satisfies(item -> {
                assertThat(item.variantId()).isEqualTo(variant);
                assertThat(item.productName()).isEqualTo("New catalogue name");
                assertThat(item.size()).isEqualTo(5);
                assertThat(item.quantity()).isEqualTo(2);
                assertThat(item.unitPrice()).isEqualTo("150000");
                assertThat(item.lineTotal()).isEqualTo("300000");
            });
        });
        assertThat(orderQueries.find(member, orderId).payableAmount()).isEqualTo("300000");
        assertThat(orderQueries.list(other)).isEmpty();
        assertThatThrownBy(() -> orderQueries.find(other, orderId)).isInstanceOf(RetailNotFoundException.class);
        assertThatThrownBy(() -> orderQueries.find(member, UUID.randomUUID().toString())).isInstanceOf(RetailNotFoundException.class);
    }

    @Test
    void simulatedVerifiedPaymentSettlesOnceAndRejectsConflictingEvidence() throws Exception {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-paid-" + random)
            .loginEmail("fe08-paid-" + random + "@example.test").build()).getId();
        String code = "PA" + random.replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            var program = programs.saveAndFlush(PromotionProgram.voucher(TargetScope.PRODUCT, code, code, 10, 2, now, 1L));
            var revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1, ProgramKind.VOUCHER,
                "FE08 payment", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                DiscountTerms.percent(BigDecimal.TEN, BigInteger.valueOf(50_000), BigInteger.ZERO), true, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT, TargetDimension.PRODUCT, Long.toString(product)));
            program.pointToRevision(revision.getId());
            var wallet = LoyaltyWallet.open(member);
            wallet.earn(BigInteger.valueOf(500));
            wallets.saveAndFlush(wallet);
        });
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var selection = new CheckoutDtos.BenefitSelection("VOUCHER", code, "100");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        BigDecimal payable = new BigDecimal(preview.payableAmount());
        var accepted = acceptance.accept(member, "paid-" + random,
            new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true));
        transaction.executeWithoutResult(status -> checkouts.lockOwned(accepted.id(), member).orElseThrow().beginPayment());
        assertThatThrownBy(() -> simulatedPayments.simulateVerifiedPayment(member, accepted.id(), BigDecimal.ONE, "VND", "proof-" + random))
            .isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> simulatedPayments.simulateVerifiedPayment(member, accepted.id(), payable, "USD", "proof-" + random))
            .isInstanceOf(RetailConflictException.class);
        var start = new CountDownLatch(1);
        CheckoutDtos.GroupedCheckoutView paid;
        CheckoutDtos.GroupedCheckoutView replay;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { start.await(); return simulatedPayments.simulateVerifiedPayment(member, accepted.id(), payable, "VND", "proof-" + random); });
            var two = executor.submit(() -> { start.await(); return simulatedPayments.simulateVerifiedPayment(member, accepted.id(), payable, "VND", "proof-" + random); });
            start.countDown();
            paid = one.get(30, TimeUnit.SECONDS);
            replay = two.get(30, TimeUnit.SECONDS);
        }
        assertThat(paid.paymentState()).isEqualTo("PAID");
        assertThat(replay).isEqualTo(paid);
        assertThatThrownBy(() -> simulatedPayments.simulateVerifiedPayment(member, accepted.id(), payable, "VND", "other-proof"))
            .isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> cancellation.cancel(member, accepted.id())).isInstanceOf(RetailConflictException.class);
        assertThat(variants.findById(variant).orElseThrow().getRemainingUnsold()).isEqualTo(BigInteger.valueOf(2));
        BigInteger held = transaction.execute(status -> guard.readSnapshots(List.of(variant), Instant.now()).get(variant).heldQuantity());
        assertThat(held).isZero();
        var benefit = benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, accepted.id()).orElseThrow();
        assertThat(benefit.getEarnState()).isEqualTo(EarnState.GRANTED);
        assertThat(benefit.getVoucherState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(benefit.getPointsState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isZero();
    }

    @Test
    void simulatedLatePaymentDoesNotSettleOrReleaseTheHold() {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-late-" + random)
            .loginEmail("fe08-late-" + random + "@example.test").build()).getId();
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var selection = new CheckoutDtos.BenefitSelection("NONE", null, "0");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        var accepted = acceptance.accept(member, "late-" + random,
            new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true));
        transaction.executeWithoutResult(status -> checkouts.lockOwned(accepted.id(), member).orElseThrow().beginPayment());
        jdbc.update("update retail_variant_holds set expires_at = ? where grouped_checkout_id = ?",
            java.sql.Timestamp.valueOf("2020-01-01 00:00:00"), accepted.id());
        assertThatThrownBy(() -> simulatedPayments.simulateVerifiedPayment(member, accepted.id(), BigDecimal.valueOf(150_000), "VND", "late-" + random))
            .isInstanceOf(RetailConflictException.class);
        assertThat(checkouts.findById(accepted.id()).orElseThrow().getPaymentState().name()).isEqualTo("PAYMENT_INITIATED");
        assertThat(variants.findById(variant).orElseThrow().getRemainingUnsold()).isEqualTo(BigInteger.valueOf(3));
        BigInteger held = transaction.execute(status -> guard.readSnapshots(List.of(variant), Instant.now()).get(variant).heldQuantity());
        assertThat(held).isEqualTo(BigInteger.ONE);
    }

    @Test
    void zeroTotalCheckoutSettlesBenefitAndStockExactlyOnce() {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        String code = "ZT" + random.replace("-", "").substring(0, 10).toUpperCase();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-zero-" + random)
            .loginEmail("fe08-zero-" + random + "@example.test").build()).getId();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            var program = programs.saveAndFlush(PromotionProgram.voucher(TargetScope.PRODUCT, code, code, 10, 2, now, 1L));
            var revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1, ProgramKind.VOUCHER,
                "FE08 zero", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                DiscountTerms.percent(BigDecimal.valueOf(100), BigInteger.valueOf(150_000), BigInteger.ZERO), false, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT, TargetDimension.PRODUCT, Long.toString(product)));
            program.pointToRevision(revision.getId());
        });
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var selection = new CheckoutDtos.BenefitSelection("VOUCHER", code, "0");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        assertThat(preview.payableAmount()).isEqualTo("0");
        var command = new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true);
        var first = acceptance.accept(member, "zero-" + random, command);
        assertThat(acceptance.accept(member, "zero-" + random, command).id()).isEqualTo(first.id());
        assertThat(first.paymentState()).isEqualTo("PAID");
        var benefit = benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, first.id()).orElseThrow();
        assertThat(benefit.getVoucherState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(variants.findById(variant).orElseThrow().getRemainingUnsold()).isEqualTo(BigInteger.valueOf(2));
        BigInteger held = transaction.execute(status -> guard.readSnapshots(List.of(variant), Instant.now()).get(variant).heldQuantity());
        assertThat(held).isZero();
    }

    @Test
    void realVoucherAndPointsAreHeldAndReleasedOnceWhenCheckoutIsCancelled() {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        String code = "RT" + random.replace("-", "").substring(0, 10).toUpperCase();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-benefits-" + random)
            .loginEmail("fe08-benefits-" + random + "@example.test").build()).getId();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            var program = programs.saveAndFlush(PromotionProgram.voucher(TargetScope.PRODUCT, code, code, 10, 2, now, 1L));
            var revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1, ProgramKind.VOUCHER,
                "FE08 integration", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                DiscountTerms.percent(BigDecimal.TEN, BigInteger.valueOf(50_000), BigInteger.ZERO), true, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT, TargetDimension.PRODUCT, Long.toString(product)));
            program.pointToRevision(revision.getId());
            var wallet = LoyaltyWallet.open(member);
            wallet.earn(BigInteger.valueOf(500));
            wallets.saveAndFlush(wallet);
        });
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var selection = new CheckoutDtos.BenefitSelection("VOUCHER", code, "100");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        var accepted = acceptance.accept(member, "benefits-" + random,
            new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true));
        var reserved = benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, accepted.id()).orElseThrow();
        assertThat(reserved.getVoucherState()).isEqualTo(ComponentState.HELD);
        assertThat(reserved.getPointsState()).isEqualTo(ComponentState.HELD);
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isEqualTo(BigInteger.valueOf(100));

        var first = cancellation.cancel(member, accepted.id());
        assertThat(cancellation.cancel(member, accepted.id())).isEqualTo(first);
        var released = benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, accepted.id()).orElseThrow();
        assertThat(released.getVoucherState()).isEqualTo(ComponentState.RELEASED);
        assertThat(released.getPointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(released.getEarnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isZero();
        assertThat(wallets.findById(member).orElseThrow().getBalancePoints()).isEqualTo(BigInteger.valueOf(500));
    }

    @Test
    void cancellationReleasesRealHoldAndBenefitsAndReplaysWithoutRestoringTwice() throws Exception {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-cancel-" + random)
            .loginEmail("fe08-cancel-" + random + "@example.test").build()).getId();
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var selection = new CheckoutDtos.BenefitSelection("NONE", null, "0");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        var accepted = acceptance.accept(member, "cancel-accept-" + random,
            new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true));

        var start = new CountDownLatch(1);
        CheckoutDtos.CancellationView first;
        CheckoutDtos.CancellationView replay;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { start.await(); return cancellation.cancel(member, accepted.id()); });
            var two = executor.submit(() -> { start.await(); return cancellation.cancel(member, accepted.id()); });
            start.countDown();
            first = one.get(30, TimeUnit.SECONDS);
            replay = two.get(30, TimeUnit.SECONDS);
        }
        assertThat(first.checkout().paymentState()).isEqualTo("CANCELLED");
        assertThat(first.unrestoredQuantity()).isZero();
        assertThat(replay).isEqualTo(first);
        assertThat(cart.getCart(member).lines()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualTo(1));
        BigInteger remainingHold = transaction.execute(status -> guard.readSnapshots(List.of(variant), Instant.now())
            .get(variant).heldQuantity());
        assertThat(remainingHold).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void cancellationAfterPaymentInitiationKeepsInventoryHeld() {
        long product = fixture.publishedProduct(CatalogueTestSupport.STAFF, CatalogueTestSupport.MANAGER);
        long variant = variants.findByProductIdOrderByRingSizeAsc(product).getFirst().getId();
        String random = UUID.randomUUID().toString();
        long member = accounts.saveAndFlush(Account.builder().firebaseUid("fe08-started-" + random)
            .loginEmail("fe08-started-" + random + "@example.test").build()).getId();
        cart.addLine(member, new CartDtos.AddCartLineRequest(variant, 1L, 1));
        var selection = new CheckoutDtos.BenefitSelection("NONE", null, "0");
        var choices = List.of(new CheckoutDtos.BranchOrderCheckoutInput(1L, "PICKUP", null));
        var preview = quotePreview.preview(member, new CheckoutDtos.BenefitPreviewRequest(selection, choices));
        var accepted = acceptance.accept(member, "started-" + random,
            new CheckoutDtos.CreateGroupedCheckoutRequest(choices, preview.previewId(), selection, true));
        transaction.executeWithoutResult(status -> checkouts.lockOwned(accepted.id(), member).orElseThrow().beginPayment());
        assertThatThrownBy(() -> cancellation.cancel(member, accepted.id())).isInstanceOf(RetailConflictException.class);
        BigInteger held = transaction.execute(status -> guard.readSnapshots(List.of(variant), Instant.now()).get(variant).heldQuantity());
        assertThat(held).isEqualTo(BigInteger.ONE);
        assertThat(cart.getCart(member).lines()).isEmpty();
    }

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
