package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalCheckoutCancellationServiceTest {
    private final RetailCartRepository carts = mock(RetailCartRepository.class);
    private final GroupedCheckoutRepository checkouts = mock(GroupedCheckoutRepository.class);
    private final CheckoutHoldRepository holds = mock(CheckoutHoldRepository.class);
    private final RetailCheckoutService views = mock(RetailCheckoutService.class);
    private final LocalCheckoutCancellationService service = new LocalCheckoutCancellationService(carts, checkouts, holds, views);

    @Test
    void localCancellationRestoresCartAndMarksDemoExpiryOnce() {
        RetailCart cart = new RetailCart(1L);
        cart.addLine(101L, 10L, 1);
        GroupedCheckout checkout = checkout();
        CheckoutHold hold = new CheckoutHold(checkout, Instant.now().plusSeconds(900));
        when(carts.findLockedByMemberAccountId(1L)).thenReturn(Optional.of(cart));
        when(checkouts.lockOwned("checkout-1", 1L)).thenReturn(Optional.of(checkout));
        when(holds.findByGroupedCheckoutId("checkout-1")).thenReturn(Optional.of(hold));

        service.cancelAsDemoExpiry(1L, "checkout-1");
        service.cancelAsDemoExpiry(1L, "checkout-1");

        assertThat(cart.getLines()).singleElement().satisfies(line -> assertThat(line.getQuantity()).isEqualTo(3));
        assertThat(checkout.getPaymentState()).isEqualTo(PaymentState.EXPIRED);
        assertThat(checkout.getOrders().getFirst().getPaymentState()).isEqualTo(PaymentState.EXPIRED);
        assertThat(hold.getStatus()).isEqualTo("LOCAL_EXPIRED");
    }

    @Test
    void paymentInitiatedCannotBeCancelled() {
        GroupedCheckout checkout = checkout();
        checkout.beginPayment();
        when(carts.findLockedByMemberAccountId(1L)).thenReturn(Optional.of(new RetailCart(1L)));
        when(checkouts.lockOwned("checkout-1", 1L)).thenReturn(Optional.of(checkout));

        assertThatThrownBy(() -> service.cancelAsDemoExpiry(1L, "checkout-1"))
            .isInstanceOf(RetailConflictException.class);
        verifyNoInteractions(holds);
    }

    @Test
    void anotherMemberCannotCancelCheckout() {
        when(carts.findLockedByMemberAccountId(2L)).thenReturn(Optional.of(new RetailCart(2L)));
        when(checkouts.lockOwned("checkout-1", 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelAsDemoExpiry(2L, "checkout-1"))
            .isInstanceOf(RetailNotFoundException.class);
        verifyNoInteractions(holds);
    }

    private GroupedCheckout checkout() {
        var checkout = new GroupedCheckout(1L, "key", 0, BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        ReflectionTestUtils.setField(checkout, "id", "checkout-1");
        var order = new RetailOrder(checkout, 10L, "PICKUP", BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        order.addItem(new OrderItem(order, 101L, 2, BigDecimal.valueOf(100)));
        checkout.addOrder(order);
        return checkout;
    }
}
