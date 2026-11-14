package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshotChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Registers the internal publication with Spring Modulith's durable event
 * publication store. 003.4 consumes the named event in its own module; this
 * listener intentionally performs no business mutation in Operations.
 */
@Component
public class WorkshopOtaSnapshotPublicationListener {
    @ApplicationModuleListener
    public void onSnapshotChanged(WorkshopSessionOtaSnapshotChanged event) {
        // Durable publication acknowledgement is managed by Spring Modulith.
    }
}
