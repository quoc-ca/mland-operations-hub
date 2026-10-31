package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CheckoutDtos {
    private CheckoutDtos() { }

    public record BenefitSelection(@NotBlank String choice, @Size(max = 128) String voucherCode,
                                   @NotBlank String requestedPoints) { }
    public record BenefitPreviewRequest(@NotNull @Valid BenefitSelection selection,
                                        List<@Valid BranchOrderCheckoutInput> branchOrders) {
        public BenefitPreviewRequest(BenefitSelection selection) { this(selection, null); }
    }
    public record BenefitPreviewView(String previewId, String currency, String merchandiseSubtotal,
                                     boolean shippingExcluded, String discountAmount, String pointsDiscountAmount,
                                     String payableAmount, String expiresAt, String shippingTotal,
                                     boolean eligible, List<String> reasons) {
        public BenefitPreviewView {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
        public BenefitPreviewView(String previewId, String currency, String merchandiseSubtotal,
                                  boolean shippingExcluded, String discountAmount, String pointsDiscountAmount,
                                  String payableAmount, String expiresAt, String shippingTotal) {
            this(previewId, currency, merchandiseSubtotal, shippingExcluded, discountAmount, pointsDiscountAmount,
                payableAmount, expiresAt, shippingTotal, true, List.of());
        }
        public BenefitPreviewView(String previewId, String currency, String merchandiseSubtotal,
                                  boolean shippingExcluded, String discountAmount, String pointsDiscountAmount,
                                  String payableAmount, String expiresAt) {
            this(previewId, currency, merchandiseSubtotal, shippingExcluded, discountAmount, pointsDiscountAmount,
                payableAmount, expiresAt, "0");
        }
    }
    public record ShippingQuoteRequest(@NotNull @Positive Long locationId) { }
    public record ShippingQuoteView(String quoteId, Long locationId, String currency, String deliveryFee,
                                    String totalCharge, String expiresAt) { }
    public record BranchOrderCheckoutInput(@NotNull @Positive Long locationId, @NotBlank String method,
                                           String shippingQuoteId) { }
    public record CreateGroupedCheckoutRequest(@NotEmpty List<@Valid BranchOrderCheckoutInput> branchOrders,
                                               @NotBlank String benefitPreviewId, @NotNull @Valid BenefitSelection benefitSelection,
                                               @AssertTrue Boolean benefitTermsAccepted) { }
    public record RetailOrderView(String id, Long locationId, String subtotal, String shippingCharge,
                                  String payableAmount, String paymentState, String fulfilmentState) { }
    public record GroupedCheckoutView(String id, List<RetailOrderView> branchOrders, String combinedSubtotal,
                                      String combinedShippingCharge, String combinedPayableAmount, String paymentState,
                                      String paymentInitiatedAt) { }
    public record PaymentRedirectView(String checkoutId, String paymentUrl, String expiresAt, String paymentState) { }
    public record CancellationView(GroupedCheckoutView checkout, int unrestoredQuantity, String message) { }
}
