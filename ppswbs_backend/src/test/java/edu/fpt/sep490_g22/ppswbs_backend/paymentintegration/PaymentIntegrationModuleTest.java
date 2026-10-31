package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class PaymentIntegrationModuleTest {
    @Test
    void modulesRemainAcyclicWithOneWayInboundDispatch() {
        ApplicationModules.of("edu.fpt.sep490_g22.ppswbs_backend.features").verify();
    }
}
