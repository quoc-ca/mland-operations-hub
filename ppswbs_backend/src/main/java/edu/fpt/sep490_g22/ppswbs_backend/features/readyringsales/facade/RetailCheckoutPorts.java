package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RetailCheckoutPorts {
    private RetailCheckoutPorts() { }

    public record PricedLine(Long variantId, Long locationId, int quantity, BigDecimal unitPrice) { }
    public record BenefitSelection(String choice, String voucherCode, BigDecimal requestedPoints) { }
    public record BenefitPreview(String previewId, BigDecimal discountAmount, BigDecimal pointsDiscountAmount, Instant expiresAt) { }
    public record RecipientAddress(String address, String province, String district, String ward) { }
    public record ShippingQuote(String quoteId, Long locationId, BigDecimal charge, Instant expiresAt,
                                boolean deliveryAvailable, String message) {
        public ShippingQuote(String quoteId, Long locationId, BigDecimal charge, Instant expiresAt) {
            this(quoteId, locationId, charge, expiresAt, true, null);
        }
    }
    public record PaymentInitiation(String paymentUrl, Instant expiresAt, boolean locallyVerified) { }
}
