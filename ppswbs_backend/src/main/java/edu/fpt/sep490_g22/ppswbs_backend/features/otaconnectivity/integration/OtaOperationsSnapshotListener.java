package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaSyncDeliveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshotChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Consumes the persistent Operations publication after its source transaction commits. */
@Component
@RequiredArgsConstructor
public class OtaOperationsSnapshotListener {

    private final OtaSyncDeliveryService deliveryService;

    @ApplicationModuleListener
    public void onSnapshotChanged(WorkshopSessionOtaSnapshotChanged event) {
        deliveryService.acceptSnapshot(event.snapshot());
    }
}
