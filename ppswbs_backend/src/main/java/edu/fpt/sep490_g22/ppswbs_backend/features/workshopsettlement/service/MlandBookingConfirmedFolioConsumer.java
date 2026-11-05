package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link MlandBookingConfirmedEvent} to open the direct-booking single OPEN folio (D1).
 * Runs alongside {@link MlandBookingConfirmedEventConsumer} without mutating refund policy snapshots.
 */
@Component
@RequiredArgsConstructor
public class MlandBookingConfirmedFolioConsumer {
    private final DirectBookingFolioOpeningService folioOpeningService;

    @ApplicationModuleListener
    public void onBookingConfirmed(MlandBookingConfirmedEvent event) {
        if (event != null && event.bookingReference() != null) {
            folioOpeningService.openFolio(event.bookingReference(), event.confirmedAt());
        }
    }
}

