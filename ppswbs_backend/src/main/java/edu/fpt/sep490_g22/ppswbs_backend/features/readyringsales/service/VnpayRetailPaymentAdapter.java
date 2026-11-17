package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHoldStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPaymentPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Activated explicitly only after the VNPay environment has been configured, and never alongside the local demo profile. */
@Service @Profile("retail-vnpay & !local") @RequiredArgsConstructor
public class VnpayRetailPaymentAdapter implements RetailPaymentPort {
    private static final DateTimeFormatter PROVIDER_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.ofHours(7));
    private final PaymentGatewayCheckoutPort gateway;
    private final PaymentOwnerRegistrationPort owners;
    private final RetailVariantHoldRepository holds;
    @Value("${vnpay.enabled:false}") private boolean vnpayEnabled;

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public RetailCheckoutPorts.PaymentInitiation initiate(String checkoutId, BigDecimal amount) {
        if (!vnpayEnabled) throw new RetailIntegrationUnavailableException("VNPay is not enabled");
        long amountVnd;
        try { amountVnd = amount.longValueExact(); }
        catch (ArithmeticException | NullPointerException ex) { throw new RetailConflictException("Invalid payment amount"); }
        if (amountVnd <= 0) throw new RetailConflictException("Invalid payment amount");
        Instant now = Instant.now();
        var current = holds.findByGroupedCheckoutId(checkoutId);
        if (current.isEmpty() || current.stream().anyMatch(h -> h.getStatus() != RetailVariantHoldStatus.HELD || !now.isBefore(h.getExpiresAt())))
            throw new RetailConflictException("Checkout hold expired or is unavailable");
        Instant expiresAt = current.stream().map(h -> h.getExpiresAt()).min(Instant::compareTo).orElseThrow();
        if (expiresAt.isAfter(now.plusSeconds(600))) expiresAt = now.plusSeconds(600);
        String merchantReference = "RETAIL-" + checkoutId;
        owners.registerOwner(merchantReference, checkoutId, "RETAIL", amountVnd, "VND", PROVIDER_DATE.format(now));
        String url = gateway.createRetailPaymentUrl(merchantReference, checkoutId, amountVnd, now, expiresAt, null);
        return new RetailCheckoutPorts.PaymentInitiation(url, expiresAt, false);
    }
}
