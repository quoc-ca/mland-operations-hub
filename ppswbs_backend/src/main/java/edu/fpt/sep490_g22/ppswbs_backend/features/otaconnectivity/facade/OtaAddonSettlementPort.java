package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade;

import java.util.Optional;

/**
 * Settlement facade contract boundary for OTA direct add-on bill creation and queries.
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
            Long locationId,
            String mlandBookingReference
    ) {
        public CreateDirectBillCommand(String channelBookingReference, String quoteReference, String itemCode,
                String itemDescription, long amountMinor, String currencyCode, String customerConsentReference,
                String staffActorReference, Long locationId) {
            this(channelBookingReference, quoteReference, itemCode, itemDescription, amountMinor, currencyCode,
                    customerConsentReference, staffActorReference, locationId, null);
        }
    }

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

    record OtaAddonBillStatus(
            String billReference,
            String channelBookingReference,
            String quoteReference,
            String itemCode,
            String itemDescription,
            long amountMinor,
            String currencyCode,
            String lifecycleStatus,
            Long locationId,
            String paymentUrlOrQr
    ) {}

    DirectBillCreationOutcome createDirectBill(CreateDirectBillCommand command);

    default Optional<OtaAddonBillStatus> getBillStatus(String billReference) {
        return Optional.empty();
    }
}
