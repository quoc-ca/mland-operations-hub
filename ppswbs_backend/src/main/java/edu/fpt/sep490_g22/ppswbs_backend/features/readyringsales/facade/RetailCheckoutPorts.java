package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RetailCheckoutPorts {
    private RetailCheckoutPorts() { }

    public record PricedLine(Long variantId, Long locationId, int quantity, BigDecimal unitPrice) { }
    public record BenefitSelection(String choice, String voucherCode, BigDecimal requestedPoints) { }
    /** Benefit preview; maxPoints/minRedeemPoints are null when the adapter cannot tell them. */
    /**
     * {@code eligibleLineIndexes} are the priced lines the programme discount applies to (FE-06), and
     * {@code appliedVoucherCode} is null when the discount is an automatic programme rather than a voucher.
     */
    public record BenefitPreview(String previewId, BigDecimal discountAmount, BigDecimal pointsDiscountAmount, Instant expiresAt,
                                 java.math.BigInteger maxPoints, java.math.BigInteger minRedeemPoints, boolean eligible,
                                 List<String> reasons, List<Integer> eligibleLineIndexes, String appliedVoucherCode) {
        public BenefitPreview {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            eligibleLineIndexes = eligibleLineIndexes == null ? List.of() : List.copyOf(eligibleLineIndexes);
        }
        public BenefitPreview(String previewId, BigDecimal discountAmount, BigDecimal pointsDiscountAmount, Instant expiresAt,
                              java.math.BigInteger maxPoints, java.math.BigInteger minRedeemPoints, boolean eligible,
                              List<String> reasons) {
            this(previewId, discountAmount, pointsDiscountAmount, expiresAt, maxPoints, minRedeemPoints, eligible, reasons,
                List.of(), null);
        }
        public BenefitPreview(String previewId, BigDecimal discountAmount, BigDecimal pointsDiscountAmount, Instant expiresAt) {
            this(previewId, discountAmount, pointsDiscountAmount, expiresAt, null, null, true, List.of());
        }
    }
    public record RecipientAddress(String address, String province, String district, String ward,
                                   String recipientName, String recipientPhone) {
        public RecipientAddress(String address, String province, String district, String ward) {
            this(address, province, district, ward, null, null);
        }
    }
    public record ShippingQuote(String quoteId, Long locationId, BigDecimal charge, Instant expiresAt,
                                boolean deliveryAvailable, String message) {
        public ShippingQuote(String quoteId, Long locationId, BigDecimal charge, Instant expiresAt) {
            this(quoteId, locationId, charge, expiresAt, true, null);
        }
    }
    public record PaymentInitiation(String paymentUrl, Instant expiresAt, boolean locallyVerified) { }
}
