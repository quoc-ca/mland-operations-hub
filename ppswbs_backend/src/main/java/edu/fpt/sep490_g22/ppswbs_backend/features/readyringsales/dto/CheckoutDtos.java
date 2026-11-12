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
    /**
     * cartLineIds: the cart lines the Member ticked for this checkout. Null keeps the legacy
     * whole-cart behaviour; an empty list is rejected because nothing was selected.
     */
    /** paymentMethod: VNPAY (default when null) or CASH_AT_COUNTER (store pickup only, FR-08-041). */
    public record BenefitPreviewRequest(@NotNull @Valid BenefitSelection selection,
                                        List<@Valid BranchOrderCheckoutInput> branchOrders,
                                        @Size(max = 50) List<@NotBlank @Size(max = 36) String> cartLineIds,
                                        @Size(max = 24) String paymentMethod) {
        public BenefitPreviewRequest(BenefitSelection selection) { this(selection, null, null, null); }
        public BenefitPreviewRequest(BenefitSelection selection, List<BranchOrderCheckoutInput> branchOrders) {
            this(selection, branchOrders, null, null);
        }
        public BenefitPreviewRequest(BenefitSelection selection, List<BranchOrderCheckoutInput> branchOrders,
                                     List<String> cartLineIds) {
            this(selection, branchOrders, cartLineIds, null);
        }
    }
    public record BenefitPreviewView(String previewId, String currency, String merchandiseSubtotal,
                                     boolean shippingExcluded, String discountAmount, String pointsDiscountAmount,
                                     String payableAmount, String expiresAt, String shippingTotal,
                                     boolean eligible, List<String> reasons, String maxPoints, String minRedeemPoints) {
        public BenefitPreviewView {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
        public BenefitPreviewView(String previewId, String currency, String merchandiseSubtotal,
                                  boolean shippingExcluded, String discountAmount, String pointsDiscountAmount,
                                  String payableAmount, String expiresAt, String shippingTotal) {
            this(previewId, currency, merchandiseSubtotal, shippingExcluded, discountAmount, pointsDiscountAmount,
                payableAmount, expiresAt, shippingTotal, true, List.of(), null, null);
        }
        public BenefitPreviewView(String previewId, String currency, String merchandiseSubtotal,
                                  boolean shippingExcluded, String discountAmount, String pointsDiscountAmount,
                                  String payableAmount, String expiresAt) {
            this(previewId, currency, merchandiseSubtotal, shippingExcluded, discountAmount, pointsDiscountAmount,
                payableAmount, expiresAt, "0");
        }
    }
    /**
     * recipientName/recipientPhone are optional at the request-validation level because a GHTK fee quote
     * only needs the address/province/district/ward; they are required in practice once a GHTK branch
     * order is accepted, so the mock shipment carries a real recipient to show Staff.
     */
    public record RecipientAddressInput(@NotBlank @Size(max = 255) String address,
                                        @NotBlank @Size(max = 128) String province,
                                        @NotBlank @Size(max = 128) String district,
                                        @NotBlank @Size(max = 128) String ward,
                                        @Size(max = 255) String recipientName,
                                        @Size(max = 32) String recipientPhone) {
        public RecipientAddressInput(String address, String province, String district, String ward) {
            this(address, province, district, ward, null, null);
        }
    }
    public record ShippingQuoteRequest(@NotNull @Positive Long locationId, @Valid RecipientAddressInput recipient,
                                       @Size(max = 50) List<@NotBlank @Size(max = 36) String> cartLineIds) {
        public ShippingQuoteRequest(Long locationId) { this(locationId, null, null); }
        public ShippingQuoteRequest(Long locationId, RecipientAddressInput recipient) { this(locationId, recipient, null); }
    }
    public record ShippingQuoteView(String quoteId, Long locationId, String currency, String deliveryFee,
                                    String totalCharge, String expiresAt, boolean deliveryAvailable, String message) {
        public ShippingQuoteView(String quoteId, Long locationId, String currency, String deliveryFee,
                                 String totalCharge, String expiresAt) {
            this(quoteId, locationId, currency, deliveryFee, totalCharge, expiresAt, true, null);
        }
    }
    public record BranchOrderCheckoutInput(@NotNull @Positive Long locationId, @NotBlank String method,
                                           String shippingQuoteId, @Valid RecipientAddressInput recipient) {
        public BranchOrderCheckoutInput(Long locationId, String method, String shippingQuoteId) {
            this(locationId, method, shippingQuoteId, null);
        }
    }
    public record CreateGroupedCheckoutRequest(@NotEmpty List<@Valid BranchOrderCheckoutInput> branchOrders,
                                               @NotBlank String benefitPreviewId, @NotNull @Valid BenefitSelection benefitSelection,
                                               @AssertTrue Boolean benefitTermsAccepted,
                                               @Size(max = 50) List<@NotBlank @Size(max = 36) String> cartLineIds,
                                               @Size(max = 24) String paymentMethod) {
        public CreateGroupedCheckoutRequest(List<BranchOrderCheckoutInput> branchOrders, String benefitPreviewId,
                                            BenefitSelection benefitSelection, Boolean benefitTermsAccepted) {
            this(branchOrders, benefitPreviewId, benefitSelection, benefitTermsAccepted, null, null);
        }
        public CreateGroupedCheckoutRequest(List<BranchOrderCheckoutInput> branchOrders, String benefitPreviewId,
                                            BenefitSelection benefitSelection, Boolean benefitTermsAccepted,
                                            List<String> cartLineIds) {
            this(branchOrders, benefitPreviewId, benefitSelection, benefitTermsAccepted, cartLineIds, null);
        }
    }
    public record RetailOrderView(String id, Long locationId, String subtotal, String shippingCharge,
                                  String payableAmount, String paymentState, String fulfilmentState) { }
    public record GroupedCheckoutView(String id, List<RetailOrderView> branchOrders, String combinedSubtotal,
                                      String combinedShippingCharge, String combinedPayableAmount, String paymentState,
                                      String paymentInitiatedAt, String paymentMethod, String counterPaymentDeadline) {
        public GroupedCheckoutView(String id, List<RetailOrderView> branchOrders, String combinedSubtotal,
                                   String combinedShippingCharge, String combinedPayableAmount, String paymentState,
                                   String paymentInitiatedAt) {
            this(id, branchOrders, combinedSubtotal, combinedShippingCharge, combinedPayableAmount, paymentState,
                paymentInitiatedAt, "VNPAY", null);
        }
    }
    /** Staff confirmation of an exact counter cash payment (FR-08-045); amounts are VND integer strings. */
    public record CashCollectionRequest(@NotBlank @jakarta.validation.constraints.Pattern(regexp = "^[1-9][0-9]{0,15}$") String amountReceived) { }
    public record CashCollectionView(String collectionId, String orderId, Long locationId, String appliedAmount,
                                     String amountReceived, String changeReturned, Long confirmedBy,
                                     String confirmedAt, String businessDate, String paymentState) { }
    public record DailyCashView(Long locationId, String businessDate, int collectionCount, String totalApplied,
                                List<CashCollectionView> collections) { }
    public record PaymentRedirectView(String checkoutId, String paymentUrl, String expiresAt, String paymentState) { }
    public record CancellationView(GroupedCheckoutView checkout, int unrestoredQuantity, String message) { }
}
