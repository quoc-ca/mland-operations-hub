package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantQuantityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutAcceptanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailUnstartedExpiryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.SimulatedRetailPaymentOutcomeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutQuotePreviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T054 (accept part) on MySQL 8.4 with FE-08's REAL services (develop 3818be9): cart → {@link
 * RetailCheckoutQuotePreviewService} (previewBenefits, stored quote with termsDigest) → {@link
 * RetailCheckoutAcceptanceService} (catalogue lock → FE-08 guard + hold → reserveAcceptedTransaction, one
 * transaction), then FE-08's outcomes (develop 82d7a7d): zero-total accept (settle + grant), cancellation before
 * payment (release + finishIneligibleEarn) and payment through {@link SimulatedRetailPaymentOutcomeService}
 * (profile {@code retail-payment-sim}: only the gateway evidence is simulated; settle + grant is FE-08's code). This
 * test only plays the Member calling FE-08's services and sets up catalogue/loyalty data; nothing of FE-08 is
 * replaced. Confirmed unpaid expiry (develop e061e90): FE-08's {@link RetailUnstartedExpiryService} expires an
 * accepted checkout whose payment never started once its hold deadline passed (release + finishIneligibleEarn with
 * UNPAID_EXPIRY_CONFIRMED). The deadline is moved into the past directly on FE-08's hold rows, as FE-08's own test
 * does; FE-08's background job is switched off here so only this test runs the expiry.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "retail.unstarted-expiry.enabled=false")
@ActiveProfiles({LoyaltyTestSupport.MYSQL_PROFILE, "retail-payment-sim"})
@Import(CatalogueFlowFixture.class)
class LoyaltyRetailCheckoutIT {

    private static final long LOCATION = 1L;

    @Autowired private CatalogueFlowFixture catalogue;
    @Autowired private VariantQuantityService quantities;
    @Autowired private ProductVariantRepository variants;
    @Autowired private CatalogueSalesFacade sales;
    @Autowired private CartService carts;
    @Autowired private RetailCheckoutQuotePreviewService previews;
    @Autowired private RetailCheckoutAcceptanceService acceptance;
    @Autowired private GroupedCheckoutRepository checkouts;
    @Autowired private RetailCheckoutCancellationService cancellation;
    @Autowired private SimulatedRetailPaymentOutcomeService payments;
    @Autowired private RetailUnstartedExpiryService expiry;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accounts;
    @Autowired private LoyaltyFacade loyalty;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;
    @Autowired private TransactionTemplate transaction;

    /** 2 × 150.000 = 300.000; voucher 10% = 30.000; 100 points = 10.000 → 260.000. */
    @Test
    void acceptHoldsTheVoucherPointsAndQuantityTogetherAndOnlyOnce() {
        Sale sale = sale(5);
        String code = voucher(sale.productId());
        long member = member(500);
        carts.addLine(member, new CartDtos.AddCartLineRequest(sale.sizeId(), LOCATION, 2));
        CheckoutDtos.BenefitSelection selection = new CheckoutDtos.BenefitSelection("VOUCHER", code, "100");

        CheckoutDtos.BenefitPreviewView preview = previews.preview(member, previewRequest(selection));
        assertThat(preview.merchandiseSubtotal()).isEqualTo("300000");
        assertThat(preview.discountAmount()).isEqualTo("30000");
        assertThat(preview.pointsDiscountAmount()).isEqualTo("10000");
        assertThat(preview.payableAmount()).isEqualTo("260000");
        assertThat(wallet(member).getHeldPoints()).as("preview holds nothing").isZero();

        String key = UUID.randomUUID().toString();
        CheckoutDtos.GroupedCheckoutView accepted = acceptance.accept(member, key, acceptRequest(preview, selection));

        BenefitResult held = loyalty.findBenefitResult(SourceKind.RETAIL, accepted.id());
        assertThat(held.voucherState()).isEqualTo(ComponentState.HELD);
        assertThat(held.pointsState()).isEqualTo(ComponentState.HELD);
        assertThat(wallet(member).getHeldPoints()).isEqualTo(BigInteger.valueOf(100));
        assertThat(program(code).getHeldCount()).isEqualTo(1);
        assertThat(available(sale.sizeId())).as("FE-08 holds the 2 rings in the same transaction")
                .isEqualTo(BigInteger.valueOf(3));

        // The same idempotency key returns the same checkout and holds nothing more.
        CheckoutDtos.GroupedCheckoutView again = acceptance.accept(member, key, acceptRequest(preview, selection));
        assertThat(again.id()).isEqualTo(accepted.id());
        assertThat(wallet(member).getHeldPoints()).isEqualTo(BigInteger.valueOf(100));
        assertThat(program(code).getHeldCount()).isEqualTo(1);
        assertThat(available(sale.sizeId())).isEqualTo(BigInteger.valueOf(3));
    }

    @Test
    void aVoucherPausedAfterThePreviewRollsBackTheWholeAccept() {
        Sale sale = sale(5);
        String code = voucher(sale.productId());
        long member = member(500);
        carts.addLine(member, new CartDtos.AddCartLineRequest(sale.sizeId(), LOCATION, 2));
        CheckoutDtos.BenefitSelection selection = new CheckoutDtos.BenefitSelection("VOUCHER", code, "100");
        CheckoutDtos.BenefitPreviewView preview = previews.preview(member, previewRequest(selection));
        transaction.executeWithoutResult(status -> programs.findByCodeKey(code).orElseThrow().pause());

        String key = UUID.randomUUID().toString();
        assertThatThrownBy(() -> acceptance.accept(member, key, acceptRequest(preview, selection)))
                .isInstanceOf(RetailConflictException.class);

        // Loyalty reserved after FE-08 created its checkout and hold: everything rolled back together.
        boolean stored = transaction.execute(status -> checkouts.findByMemberAccountIdAndIdempotencyKey(member, key).isPresent());
        assertThat(stored).isFalse();
        assertThat(available(sale.sizeId())).isEqualTo(BigInteger.valueOf(5));
        assertThat(wallet(member).getHeldPoints()).isZero();
        assertThat(program(code).getHeldCount()).isZero();
    }

    @Test
    void pointsWithoutAVoucherAreHeldAndTheShippingIsNotDiscounted() {
        Sale sale = sale(5);
        long member = member(1_000);
        carts.addLine(member, new CartDtos.AddCartLineRequest(sale.sizeId(), LOCATION, 1));
        CheckoutDtos.BenefitSelection selection = new CheckoutDtos.BenefitSelection("NONE", null, "150");

        CheckoutDtos.BenefitPreviewView preview = previews.preview(member, previewRequest(selection));
        assertThat(preview.payableAmount()).isEqualTo("135000");
        CheckoutDtos.GroupedCheckoutView accepted = acceptance.accept(member, UUID.randomUUID().toString(),
                acceptRequest(preview, selection));

        assertThat(accepted.combinedShippingCharge()).isIn("0", "0.00");
        assertThat(loyalty.findBenefitResult(SourceKind.RETAIL, accepted.id()).pointsState()).isEqualTo(ComponentState.HELD);
        assertThat(wallet(member).getHeldPoints()).isEqualTo(BigInteger.valueOf(150));
    }

    @Test
    void aVerifiedPaymentConsumesTheBenefitsAndEarnsOnce() {
        Sale sale = sale(5);
        String code = voucher(sale.productId());
        long member = member(500);
        carts.addLine(member, new CartDtos.AddCartLineRequest(sale.sizeId(), LOCATION, 2));
        CheckoutDtos.BenefitSelection selection = new CheckoutDtos.BenefitSelection("VOUCHER", code, "100");
        CheckoutDtos.BenefitPreviewView preview = previews.preview(member, previewRequest(selection));
        CheckoutDtos.GroupedCheckoutView accepted = acceptance.accept(member, UUID.randomUUID().toString(),
                acceptRequest(preview, selection));
        transaction.executeWithoutResult(status -> checkouts.lockOwned(accepted.id(), member).orElseThrow().beginPayment());
        BigDecimal payable = new BigDecimal(preview.payableAmount());
        String proof = "proof-" + UUID.randomUUID();

        payments.simulateVerifiedPayment(member, accepted.id(), payable, "VND", proof);
        BigInteger balanceAfterPayment = wallet(member).getBalancePoints();
        payments.simulateVerifiedPayment(member, accepted.id(), payable, "VND", proof);

        BenefitResult result = loyalty.findBenefitResult(SourceKind.RETAIL, accepted.id());
        assertThat(result.voucherState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(result.pointsState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(result.earnState()).isEqualTo(EarnState.GRANTED);
        assertThat(wallet(member).getHeldPoints()).isZero();
        assertThat(balanceAfterPayment).as("500 - 100 used + points earned on 260.000").isGreaterThan(BigInteger.valueOf(400));
        assertThat(wallet(member).getBalancePoints()).as("a repeated payment earns nothing more").isEqualTo(balanceAfterPayment);
        assertThat(program(code).getConsumedCount()).isEqualTo(1);
        assertThat(program(code).getHeldCount()).isZero();
        // A paid checkout cannot be cancelled before payment.
        assertThatThrownBy(() -> cancellation.cancel(member, accepted.id())).isInstanceOf(RetailConflictException.class);
    }

    @Test
    void aCancellationBeforePaymentGivesBackTheVoucherAndPointsOnce() {
        Sale sale = sale(5);
        String code = voucher(sale.productId());
        long member = member(500);
        carts.addLine(member, new CartDtos.AddCartLineRequest(sale.sizeId(), LOCATION, 2));
        CheckoutDtos.BenefitSelection selection = new CheckoutDtos.BenefitSelection("VOUCHER", code, "100");
        CheckoutDtos.BenefitPreviewView preview = previews.preview(member, previewRequest(selection));
        CheckoutDtos.GroupedCheckoutView accepted = acceptance.accept(member, UUID.randomUUID().toString(),
                acceptRequest(preview, selection));

        cancellation.cancel(member, accepted.id());
        cancellation.cancel(member, accepted.id());

        BenefitResult result = loyalty.findBenefitResult(SourceKind.RETAIL, accepted.id());
        assertThat(result.voucherState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.pointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.earnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(wallet(member).getHeldPoints()).isZero();
        assertThat(wallet(member).getBalancePoints()).isEqualTo(BigInteger.valueOf(500));
        assertThat(program(code).getHeldCount()).isZero();
        assertThat(program(code).getConsumedCount()).isZero();
        assertThat(available(sale.sizeId())).isEqualTo(BigInteger.valueOf(5));
    }

    /** A fixed 150.000 voucher on one 150.000 ring: nothing to pay, settled and earned (zero) at accept. */
    @Test
    void aZeroTotalCheckoutSettlesAtAcceptAndEarnsNothing() {
        Sale sale = sale(5);
        String code = voucher(sale.productId(), DiscountTerms.fixed(BigInteger.valueOf(150_000), BigInteger.valueOf(150_000),
                BigInteger.ZERO));
        long member = member(500);
        carts.addLine(member, new CartDtos.AddCartLineRequest(sale.sizeId(), LOCATION, 1));
        CheckoutDtos.BenefitSelection selection = new CheckoutDtos.BenefitSelection("VOUCHER", code, "0");
        CheckoutDtos.BenefitPreviewView preview = previews.preview(member, previewRequest(selection));
        assertThat(preview.payableAmount()).isEqualTo("0");

        CheckoutDtos.GroupedCheckoutView accepted = acceptance.accept(member, UUID.randomUUID().toString(),
                acceptRequest(preview, selection));

        assertThat(accepted.paymentState()).isEqualTo("PAID");
        BenefitResult result = loyalty.findBenefitResult(SourceKind.RETAIL, accepted.id());
        assertThat(result.voucherState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(result.earnState()).isEqualTo(EarnState.GRANTED);
        assertThat(wallet(member).getBalancePoints()).as("zero earn writes no points").isEqualTo(BigInteger.valueOf(500));
        assertThat(program(code).getConsumedCount()).isEqualTo(1);
        assertThat(remaining(sale.sizeId())).as("the ring is sold at accept").isEqualTo(BigInteger.valueOf(4));
    }

    @Test
    void aConfirmedUnpaidExpiryGivesBackTheVoucherAndPointsOnce() {
        Sale sale = sale(5);
        String code = voucher(sale.productId());
        long member = member(500);
        carts.addLine(member, new CartDtos.AddCartLineRequest(sale.sizeId(), LOCATION, 2));
        CheckoutDtos.BenefitSelection selection = new CheckoutDtos.BenefitSelection("VOUCHER", code, "100");
        CheckoutDtos.BenefitPreviewView preview = previews.preview(member, previewRequest(selection));
        CheckoutDtos.GroupedCheckoutView accepted = acceptance.accept(member, UUID.randomUUID().toString(),
                acceptRequest(preview, selection));

        // Before the deadline nothing is released.
        assertThat(expiry.expire(member, accepted.id())).isFalse();
        assertThat(loyalty.findBenefitResult(SourceKind.RETAIL, accepted.id()).voucherState()).isEqualTo(ComponentState.HELD);

        jdbc.update("update retail_variant_holds set expires_at = ? where grouped_checkout_id = ?",
                Timestamp.valueOf("2020-01-01 00:00:00"), accepted.id()); // far past in any time zone
        assertThat(expiry.expire(member, accepted.id())).isTrue();
        assertThat(expiry.expire(member, accepted.id())).as("a second run changes nothing").isFalse();

        BenefitResult result = loyalty.findBenefitResult(SourceKind.RETAIL, accepted.id());
        assertThat(result.voucherState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.pointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.earnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(wallet(member).getHeldPoints()).isZero();
        assertThat(wallet(member).getBalancePoints()).isEqualTo(BigInteger.valueOf(500));
        assertThat(program(code).getHeldCount()).isZero();
        assertThat(program(code).getConsumedCount()).isZero();
        assertThat(available(sale.sizeId())).isEqualTo(BigInteger.valueOf(5));
        // An expired checkout cannot be cancelled afterwards.
        assertThatThrownBy(() -> cancellation.cancel(member, accepted.id())).isInstanceOf(RetailConflictException.class);
    }

    // ---- helpers ----

    private record Sale(long productId, long sizeId) {
    }

    /** A published ring (size 5 at 150.000) with {@code quantity} left, set under FE-08's real guard. */
    private Sale sale(long quantity) {
        long productId = catalogue.publishedProduct(STAFF, MANAGER);
        ProductVariant size = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId)).get(0);
        quantities.setQuantity(STAFF, UUID.randomUUID(), size.getId().toString(), size.getVersion(), Long.toString(quantity));
        return new Sale(productId, size.getId());
    }

    /** An active product voucher on the product: 10%, cap 50.000, no minimum, combinable with points. */
    private String voucher(long productId) {
        return voucher(productId, DiscountTerms.percent(new BigDecimal("10"), BigInteger.valueOf(50_000), BigInteger.ZERO));
    }

    private String voucher(long productId, DiscountTerms terms) {
        String code = "CK" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.PRODUCT, code, code, 10, 2, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Checkout IT", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                    terms, true, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT, TargetDimension.PRODUCT,
                    Long.toString(productId)));
            program.pointToRevision(revision.getId());
        });
        return code;
    }

    /** A Member account (FE-08 carts reference accounts) with {@code points} in the wallet. */
    private long member(long points) {
        String uid = "loyalty-checkout-it-" + UUID.randomUUID();
        long id = transaction.execute(status -> accounts.saveAndFlush(Account.builder().firebaseUid(uid)
                .loginEmail(uid + "@example.test").role(AccountRole.MEMBER).status(AccountStatus.ACTIVE).build()).getId());
        transaction.executeWithoutResult(status -> {
            LoyaltyWallet wallet = LoyaltyWallet.open(id);
            wallet.earn(BigInteger.valueOf(points));
            wallets.saveAndFlush(wallet);
        });
        return id;
    }

    private static CheckoutDtos.BenefitPreviewRequest previewRequest(CheckoutDtos.BenefitSelection selection) {
        return new CheckoutDtos.BenefitPreviewRequest(selection,
                List.of(new CheckoutDtos.BranchOrderCheckoutInput(LOCATION, "PICKUP", null)));
    }

    private static CheckoutDtos.CreateGroupedCheckoutRequest acceptRequest(CheckoutDtos.BenefitPreviewView preview,
                                                                           CheckoutDtos.BenefitSelection selection) {
        return new CheckoutDtos.CreateGroupedCheckoutRequest(
                List.of(new CheckoutDtos.BranchOrderCheckoutInput(LOCATION, "PICKUP", null)),
                preview.previewId(), selection, true);
    }

    private BigInteger available(long sizeId) {
        return sales.readSaleFacts(List.of(sizeId)).get(0).availableQuantity();
    }

    private BigInteger remaining(long sizeId) {
        return transaction.execute(status -> variants.findById(sizeId).orElseThrow().getRemainingUnsold());
    }

    private LoyaltyWallet wallet(long member) {
        return transaction.execute(status -> wallets.findById(member).orElseThrow());
    }

    private PromotionProgram program(String code) {
        return transaction.execute(status -> programs.findByCodeKey(code).orElseThrow());
    }
}
