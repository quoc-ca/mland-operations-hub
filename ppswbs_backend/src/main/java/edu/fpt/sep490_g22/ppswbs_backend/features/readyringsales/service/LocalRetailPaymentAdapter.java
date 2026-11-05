package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPaymentPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Local-only VNPay substitute. The verification result is server-side mock data, not a browser callback. */
@Service
@Profile("local")
public class LocalRetailPaymentAdapter implements RetailPaymentPort {
    @Override public RetailCheckoutPorts.PaymentInitiation initiate(String checkoutId, BigDecimal amount) {
        return new RetailCheckoutPorts.PaymentInitiation("https://local.mock/vnpay/checkout/" + checkoutId,
            Instant.now().plus(15, ChronoUnit.MINUTES), true);
    }
}
