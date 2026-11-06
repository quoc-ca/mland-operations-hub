package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the fictional GHTK shipment state machine. Staff drives each transition by hand;
 * skips, repeats, and post-delivery moves must be rejected.
 */
class RetailShipmentLifecycleTest {

    @Test
    void advancesStrictlyThroughAllowedTransitionsToDelivered() {
        var checkout = new GroupedCheckout(1L, "key-1", 1L, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO);
        var order = new RetailOrder(checkout, 10L, "GHTK", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO);
        var shipment = new RetailShipment(checkout, order, "S1234567890",
            "Nguyen Van A", "0901234567", "123 Duong A", "HN", "Cau Giay", "Dich Vong");

        assertThat(shipment.getStatus()).isEqualTo(RetailShipmentStatus.PREPARING);
        assertThat(shipment.getMockTrackingNumber()).isEqualTo("S1234567890");

        shipment.advanceTo(RetailShipmentStatus.HANDED_TO_CARRIER, 100L, "STAFF#100", "Handed over");
        assertThat(shipment.getStatus()).isEqualTo(RetailShipmentStatus.HANDED_TO_CARRIER);

        shipment.advanceTo(RetailShipmentStatus.IN_TRANSIT, 100L, "STAFF#100", "Courier departed");
        assertThat(shipment.getStatus()).isEqualTo(RetailShipmentStatus.IN_TRANSIT);

        shipment.advanceTo(RetailShipmentStatus.DELIVERED, 100L, "STAFF#100", "Delivered to recipient");
        assertThat(shipment.getStatus()).isEqualTo(RetailShipmentStatus.DELIVERED);
        assertThat(shipment.getStatus().isTerminal()).isTrue();
        assertThat(shipment.getHistory()).hasSize(3);
    }

    @Test
    void rejectsSkippedTransition() {
        var checkout = new GroupedCheckout(1L, "key-1", 1L, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO);
        var order = new RetailOrder(checkout, 10L, "GHTK", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO);
        var shipment = new RetailShipment(checkout, order, "S1234567890",
            "Nguyen Van A", "0901234567", "123 Duong A", "HN", "Cau Giay", "Dich Vong");

        assertThatThrownBy(() -> shipment.advanceTo(RetailShipmentStatus.DELIVERED, 100L, "STAFF#100", "Jump to end"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot move shipment from PREPARING to DELIVERED");
    }

    @Test
    void rejectsTransitionAfterDelivery() {
        var checkout = new GroupedCheckout(1L, "key-1", 1L, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO);
        var order = new RetailOrder(checkout, 10L, "GHTK", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO);
        var shipment = new RetailShipment(checkout, order, "S1234567890",
            "Nguyen Van A", "0901234567", "123 Duong A", "HN", "Cau Giay", "Dich Vong");

        shipment.advanceTo(RetailShipmentStatus.HANDED_TO_CARRIER, 100L, "STAFF#100", null);
        shipment.advanceTo(RetailShipmentStatus.IN_TRANSIT, 100L, "STAFF#100", null);
        shipment.advanceTo(RetailShipmentStatus.DELIVERED, 100L, "STAFF#100", null);

        assertThatThrownBy(() -> shipment.advanceTo(RetailShipmentStatus.IN_TRANSIT, 100L, "STAFF#100", "Reopen"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Shipment already delivered; no further transition is possible");
    }
}
