package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

/** Public producer boundary owned by Workshop Settlement. */
public interface WorkshopSettlementFacade {
    CancellationHandoffOutcome openCancellationHandoff(OpenCancellationHandoffCommand command);

    SettlementPaymentEvidenceOutcome applyVerifiedSettlementPaymentEvidence(
            ApplyVerifiedSettlementPaymentEvidenceCommand command);

    /** Feature 007 calls this only after receiving the matching ready event. */
    SettlementNotificationDeliveryPayload getSettlementNotificationDeliveryPayload(
            SettlementNotificationDeliveryPayloadQuery query);

    DirectShopAddonBillOutcome createDirectShopAddonBill(DirectShopAddonBillCommand command);

    record DirectShopAddonBillCommand(
            String channelBookingReference,
            String quoteReference,
            String itemCode,
            String itemDescription,
            long amountMinor,
            String currencyCode,
            String customerConsentReference,
            String staffActorReference,
            Long locationId) {}

    record DirectShopAddonBillOutcome(
            boolean created,
            String billReference,
            String paymentUrlOrQr,
            String errorCode,
            String errorMessage) {
        public static DirectShopAddonBillOutcome success(String billReference) {
            return new DirectShopAddonBillOutcome(true, billReference, null, null, null);
        }
        public static DirectShopAddonBillOutcome rejected(String code, String message) {
            return new DirectShopAddonBillOutcome(false, null, null, code, message);
        }
    }
}
