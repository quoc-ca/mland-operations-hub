package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cash checkout with branch orders decided one by one (Session 2026-11-13, D-011). */
class RetailBranchOrderStateTest {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static GroupedCheckout checkoutWithTwoBranches() {
        var checkout = new GroupedCheckout(1L, "key", 0L, BigDecimal.valueOf(500_000), ZERO, BigDecimal.valueOf(50_000));
        checkout.addOrder(new RetailOrder(checkout, 10L, "PICKUP", BigDecimal.valueOf(200_000), ZERO, BigDecimal.valueOf(50_000)));
        checkout.addOrder(new RetailOrder(checkout, 20L, "PICKUP", BigDecimal.valueOf(300_000), ZERO, ZERO));
        return checkout;
    }

    @Test
    void checkoutStaysOpenUntilEveryBranchOrderIsDecided() {
        var checkout = checkoutWithTwoBranches();
        checkout.getOrders().get(0).markPaid();
        checkout.resolveFromOrders();
        assertThat(checkout.getPaymentState()).isEqualTo(PaymentState.ACCEPTED_UNPAID);
        checkout.getOrders().get(1).cancel();
        checkout.resolveFromOrders();
        assertThat(checkout.getPaymentState()).isEqualTo(PaymentState.PAID);
    }

    @Test
    void checkoutIsCancelledWhenAllWereCancelledAndExpiredOtherwise() {
        var cancelled = checkoutWithTwoBranches();
        cancelled.getOrders().forEach(RetailOrder::cancel);
        cancelled.resolveFromOrders();
        assertThat(cancelled.getPaymentState()).isEqualTo(PaymentState.CANCELLED);

        var expired = checkoutWithTwoBranches();
        expired.getOrders().get(0).cancel();
        expired.getOrders().get(1).expireUnpaid();
        expired.resolveFromOrders();
        assertThat(expired.getPaymentState()).isEqualTo(PaymentState.EXPIRED);
    }

    @Test
    void branchOrderTakesTheShareFixedByLoyaltyAndNeverGoesNegative() {
        var order = checkoutWithTwoBranches().getOrders().get(1);
        order.applyDiscount(BigDecimal.valueOf(30_000));
        assertThat(order.getPayableAmount()).isEqualByComparingTo("270000");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("30000");
        assertThatThrownBy(() -> order.applyDiscount(BigDecimal.valueOf(300_001))).isInstanceOf(IllegalArgumentException.class);
    }
}
