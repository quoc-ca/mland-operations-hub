package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade;

/**
 * Settlement facade contract boundary for OTA direct add-on bill creation.
 */
public interface OtaAddonSettlementPort {

    record CreateDirectBillCommand(
            String channelBookingReference,
            String quoteReference,
            String itemCode,
            String itemDescription,
            long amountMinor,
            String currencyCode,
            String customerConsentReference,
            String staffActorReference,
            Long locationId
    ) {}

    record DirectBillCreationOutcome(
            boolean created,
            String settlementBillReference,
            String paymentUrlOrQr,
            String errorCode,
            String errorMessage
    ) {
        public static DirectBillCreationOutcome success(String billReference, String paymentUrlOrQr) {
            return new DirectBillCreationOutcome(true, billReference, paymentUrlOrQr, null, null);
        }

        public static DirectBillCreationOutcome rejected(String errorCode, String errorMessage) {
            return new DirectBillCreationOutcome(false, null, null, errorCode, errorMessage);
        }
    }

    DirectBillCreationOutcome createDirectBill(CreateDirectBillCommand command);
}

