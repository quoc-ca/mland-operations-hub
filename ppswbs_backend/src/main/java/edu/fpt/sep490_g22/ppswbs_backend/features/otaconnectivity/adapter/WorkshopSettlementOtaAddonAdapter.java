package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
                        command.customerConsentReference(), command.staffActorReference(), command.locationId(),
                        command.mlandBookingReference()));
        return outcome.created()
                ? DirectBillCreationOutcome.success(outcome.billReference(), outcome.paymentUrlOrQr())
                : DirectBillCreationOutcome.rejected(outcome.errorCode(), outcome.errorMessage());
    }

    @Override
    public Optional<OtaAddonBillStatus> getBillStatus(String billReference) {
        if (billReference == null || billReference.isBlank()) return Optional.empty();
        return settlement.findOtaAddonBill(billReference).map(s -> new OtaAddonBillStatus(
                s.billReference(),
                s.channelBookingReference(),
                s.quoteReference(),
                s.itemCode(),
                s.itemDescription(),
                s.amountMinor(),
                s.currencyCode(),
                s.lifecycleStatus(),
                s.locationId(),
                s.paymentUrlOrQr()
        ));
    }
}
