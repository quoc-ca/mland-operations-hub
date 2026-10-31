package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutAcceptanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutQuotePreviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.SimulatedRetailPaymentOutcomeService;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/**
 * Drives FE-08's REAL checkout services the way a Member does (develop b367505): cart → quote preview → accept
 * (catalogue lock → FE-08 guard + hold → loyalty reserve) → payment or cancellation. Payment goes through FE-08's
 * {@code SimulatedRetailPaymentOutcomeService} (profile {@code retail-payment-sim}): only the gateway evidence is
 * simulated, the settlement path into productmanagement and loyalty is FE-08's production code. Moving the checkout
 * to PAYMENT_INITIATED uses FE-08's own entity transition, since the real gateway port is not configured in tests.
 * Use only with {@code @ActiveProfiles({..., "retail-payment-sim"})}.
 */
@TestComponent
public class RealRetailCheckout {

    public static final String PROFILE = "retail-payment-sim";
    public static final long LOCATION = 1L;

    public record Accepted(long memberId, String checkoutId, BigDecimal payable) {
    }

    private final CartService carts;
    private final RetailCheckoutQuotePreviewService previews;
    private final RetailCheckoutAcceptanceService acceptance;
    private final RetailCheckoutCancellationService cancellation;
    private final SimulatedRetailPaymentOutcomeService payments;
    private final GroupedCheckoutRepository checkouts;
    private final AccountRepository accounts;
    private final TransactionTemplate transaction;

    public RealRetailCheckout(CartService carts, RetailCheckoutQuotePreviewService previews,
                              RetailCheckoutAcceptanceService acceptance, RetailCheckoutCancellationService cancellation,
                              SimulatedRetailPaymentOutcomeService payments, GroupedCheckoutRepository checkouts,
                              AccountRepository accounts, TransactionTemplate transaction) {
        this.carts = carts;
        this.previews = previews;
        this.acceptance = acceptance;
        this.cancellation = cancellation;
        this.payments = payments;
        this.checkouts = checkouts;
        this.accounts = accounts;
        this.transaction = transaction;
    }

    public long newMember() {
        String uid = "pm-checkout-it-" + UUID.randomUUID();
        return transaction.execute(status -> accounts.saveAndFlush(Account.builder().firebaseUid(uid)
                .loginEmail(uid + "@example.test").role(AccountRole.MEMBER).status(AccountStatus.ACTIVE).build()).getId());
    }

    /** A new Member puts {@code quantity} of the size in the cart, previews without benefits and accepts. */
    public Accepted accept(long sizeId, int quantity) {
        return accept(newMember(), sizeId, quantity);
    }

    public Accepted accept(long memberId, long sizeId, int quantity) {
        carts.addLine(memberId, new CartDtos.AddCartLineRequest(sizeId, LOCATION, quantity));
        CheckoutDtos.BenefitPreviewView preview = preview(memberId);
        CheckoutDtos.GroupedCheckoutView accepted = acceptance.accept(memberId, UUID.randomUUID().toString(),
                new CheckoutDtos.CreateGroupedCheckoutRequest(pickup(), preview.previewId(), noBenefit(), true));
        return new Accepted(memberId, accepted.id(), new BigDecimal(preview.payableAmount()));
    }

    /** Quote preview of the Member's current cart, no voucher and no points. */
    public CheckoutDtos.BenefitPreviewView preview(long memberId) {
        return previews.preview(memberId, new CheckoutDtos.BenefitPreviewRequest(noBenefit(), pickup()));
    }

    public CheckoutDtos.GroupedCheckoutView acceptPreview(long memberId, CheckoutDtos.BenefitPreviewView preview) {
        return acceptance.accept(memberId, UUID.randomUUID().toString(),
                new CheckoutDtos.CreateGroupedCheckoutRequest(pickup(), preview.previewId(), noBenefit(), true));
    }

    /** Verified payment of the full payable amount with this evidence reference (repeatable). */
    public CheckoutDtos.GroupedCheckoutView pay(Accepted accepted, String proof) {
        transaction.executeWithoutResult(status -> {
            var checkout = checkouts.lockOwned(accepted.checkoutId(), accepted.memberId()).orElseThrow();
            if (checkout.getPaymentState() == PaymentState.ACCEPTED_UNPAID) {
                checkout.beginPayment();
            }
        });
        return payments.simulateVerifiedPayment(accepted.memberId(), accepted.checkoutId(), accepted.payable(), "VND", proof);
    }

    public CheckoutDtos.CancellationView cancel(Accepted accepted) {
        return cancellation.cancel(accepted.memberId(), accepted.checkoutId());
    }

    public String paymentState(Accepted accepted) {
        return transaction.execute(status -> checkouts.findById(accepted.checkoutId()).orElseThrow().getPaymentState().name());
    }

    /** Unit price FE-08 stored on the order line of the size. */
    public BigInteger unitPrice(Accepted accepted, long sizeId) {
        return transaction.execute(status -> checkouts.findById(accepted.checkoutId()).orElseThrow().getOrders().stream()
                .flatMap(order -> order.getItems().stream()).filter(item -> item.getVariantId() == sizeId)
                .findFirst().orElseThrow().getUnitPrice().toBigIntegerExact());
    }

    private static List<CheckoutDtos.BranchOrderCheckoutInput> pickup() {
        return List.of(new CheckoutDtos.BranchOrderCheckoutInput(LOCATION, "PICKUP", null));
    }

    private static CheckoutDtos.BenefitSelection noBenefit() {
        return new CheckoutDtos.BenefitSelection("NONE", null, "0");
    }
}
