package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

class WorkshopSettlementProblemHandlerOrderTest {
    /** Without this order the application-wide catch-all handler answers 500 for every settlement 404/409. */
    @Test
    void settlementProblemHandlerOutranksTheApplicationWideCatchAll() {
        Order order = WorkshopSettlementProblemHandler.class.getAnnotation(Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
