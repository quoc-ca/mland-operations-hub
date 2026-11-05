package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import org.springframework.stereotype.Component;

@Component
public final class WorkshopSettlementOtaAddonAdapter implements OtaAddonSettlementPort {
    private final WorkshopSettlementFacade settlement;

    public WorkshopSettlementOtaAddonAdapter(WorkshopSettlementFacade settlement) {
        this.settlement = settlement;
    }

    @Override
    public DirectBillCreationOutcome createDirectBill(CreateDirectBillCommand command) {
        if (command == null) return DirectBillCreationOutcome.rejected("VALIDATION_ERROR", "command is required");
        WorkshopSettlementFacade.DirectShopAddonBillOutcome outcome = settlement.createDirectShopAddonBill(
                new WorkshopSettlementFacade.DirectShopAddonBillCommand(
                        command.channelBookingReference(), command.quoteReference(), command.itemCode(),
                        command.itemDescription(), command.amountMinor(), command.currencyCode(),
                        command.customerConsentReference(), command.staffActorReference(), command.locationId()));
        return outcome.created()
                ? DirectBillCreationOutcome.success(outcome.billReference(), outcome.paymentUrlOrQr())
                : DirectBillCreationOutcome.rejected(outcome.errorCode(), outcome.errorMessage());
    }
}
