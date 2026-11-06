package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPaymentPort;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Local-only VNPay substitute. The verification result is server-side mock data, not a browser callback.
 *
 * <p>Active for every local run, including one where {@code retail-vnpay} is also enabled: the local
 * checkout flow only ever creates a checkout-level hold, never a variant-level hold, so the VNPay payment
 * adapter (which requires the latter) must stay disabled under {@code local} regardless of
 * {@code retail-vnpay}. Keeping this bean present is also what stops the two adapters from leaving no
 * {@link RetailPaymentPort} at all.
 */
@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalRetailPaymentAdapter implements RetailPaymentPort {

    private final Environment environment;

    @PostConstruct
    void warnWhenVnPayProfileIsIgnored() {
        if (environment.matchesProfiles("retail-vnpay")) {
            log.warn("Profiles 'local' and 'retail-vnpay' are both active. The local demo payment adapter is used "
                + "and no VNPay request is created. To exercise VNPay, run with 'retail-vnpay' and without 'local'.");
        }
    }

    @Override public RetailCheckoutPorts.PaymentInitiation initiate(String checkoutId, BigDecimal amount) {
        return new RetailCheckoutPorts.PaymentInitiation("https://local.mock/vnpay/checkout/" + checkoutId,
            Instant.now().plus(15, ChronoUnit.MINUTES), true);
    }
}
