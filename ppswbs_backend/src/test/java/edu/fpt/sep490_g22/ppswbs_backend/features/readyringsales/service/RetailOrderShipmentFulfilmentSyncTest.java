package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused checks on the guarded fulfilment-state mirror used by Staff shipment transitions.
 * Existing snapshot fields (recipient columns) are untouched by this method.
 */
class RetailOrderShipmentFulfilmentSyncTest {

    private RetailOrder ghtkOrder() {
        var checkout = new GroupedCheckout(1L, "key", 0, BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        return new RetailOrder(checkout, 5L, "GHTK", BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void mirrorsEachShipmentStatus() {
        var order = ghtkOrder();
        order.syncShipmentFulfilmentState(RetailShipmentStatus.PREPARING);
        assertThat(order.getFulfilmentState()).isEqualTo("SHIPMENT_PENDING");
        order.syncShipmentFulfilmentState(RetailShipmentStatus.HANDED_TO_CARRIER);
        assertThat(order.getFulfilmentState()).isEqualTo("HANDED_TO_CARRIER");
        order.syncShipmentFulfilmentState(RetailShipmentStatus.IN_TRANSIT);
        assertThat(order.getFulfilmentState()).isEqualTo("IN_TRANSIT");
        order.syncShipmentFulfilmentState(RetailShipmentStatus.DELIVERED);
        assertThat(order.getFulfilmentState()).isEqualTo("DELIVERED");
    }

    @Test
    void pickupOrderIsUntouched() {
        var checkout = new GroupedCheckout(1L, "key", 0, BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        var pickup = new RetailOrder(checkout, 5L, "PICKUP", BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        pickup.markPickupReady();
        pickup.syncShipmentFulfilmentState(RetailShipmentStatus.DELIVERED);
        assertThat(pickup.getFulfilmentState()).isEqualTo("READY_FOR_PICKUP");
        assertThat(pickup.isPickupReadyConfirmed()).isTrue();
    }

    @Test
    void nullStatusIsNoOp() {
        var order = ghtkOrder();
        order.syncShipmentFulfilmentState(null);
        assertThat(order.getFulfilmentState()).isEqualTo("SHIPMENT_PENDING");
    }
}
