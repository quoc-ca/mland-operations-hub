package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.OrderItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RetailCartRestorationTest {
    @Test
    void preservesFullCartAndReportsItemsThatDoNotFit() {
        var cart = new RetailCart(1L);
        for (long variant = 1; variant <= 50; variant++) cart.addLine(variant, 10L, 1);
        var checkout = checkout(51L, 2);
        assertThat(RetailCartRestoration.restore(cart, checkout)).isEqualTo(2);
        assertThat(cart.getLines()).hasSize(50);
    }

    @Test
    void restoresOnlyQuantityWithinVariantLimitAcrossLocations() {
        var cart = new RetailCart(1L);
        cart.addLine(101L, 20L, 97);
        var checkout = checkout(101L, 5);
        assertThat(RetailCartRestoration.restore(cart, checkout)).isEqualTo(3);
        assertThat(cart.getLines()).hasSize(2);
        assertThat(cart.getLines()).extracting("quantity").containsExactly(97, 2);
    }

    private GroupedCheckout checkout(long variant, int quantity) {
        var checkout = new GroupedCheckout(1L, "key", 0, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        var order = new RetailOrder(checkout, 10L, "PICKUP", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        order.addItem(new OrderItem(order, variant, quantity, BigDecimal.ONE));
        checkout.addOrder(order);
        return checkout;
    }
}
