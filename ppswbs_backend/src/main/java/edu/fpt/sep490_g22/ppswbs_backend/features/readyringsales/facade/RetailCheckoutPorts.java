package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RetailCheckoutPorts {
    private RetailCheckoutPorts() { }

    public record PricedLine(String variantId, Long locationId, int quantity, BigDecimal unitPrice) { }
    public record BenefitSelection(String choice, String voucherCode, BigDecimal requestedPoints) { }
    public record BenefitPreview(String previewId, BigDecimal discountAmount, BigDecimal pointsDiscountAmount, Instant expiresAt) { }
    public record ShippingQuote(String quoteId, Long locationId, BigDecimal charge, Instant expiresAt) { }
    public record PaymentInitiation(String paymentUrl, Instant expiresAt, boolean locallyVerified) { }
}
