package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort;
import org.springframework.stereotype.Component;

/**
 * Safe default until Settlement exposes the approved OTA add-on billing facade.
 *
 * The OTA module must still be loadable in the full application context, but it
 * must fail closed rather than create a bill without Settlement ownership,
 * payment-owner registration, and verified payment evidence.
 */
@Component
public final class UnavailableOtaAddonSettlementAdapter implements OtaAddonSettlementPort {
    @Override
    public DirectBillCreationOutcome createDirectBill(CreateDirectBillCommand command) {
        return DirectBillCreationOutcome.rejected(
                "SETTLEMENT_ADDON_BILLING_UNAVAILABLE",
                "Settlement has not exposed the approved OTA add-on billing facade yet");
    }
}
