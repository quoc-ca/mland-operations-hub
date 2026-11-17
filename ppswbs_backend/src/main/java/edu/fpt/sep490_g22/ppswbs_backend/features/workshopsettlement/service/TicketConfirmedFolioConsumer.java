package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandTicketConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link MlandTicketConfirmedEvent} to open one OPEN folio per confirmed ticket of a booking group
 * (003.3 FR-030, T093). 003.1 publishes one event per ticket, so a group of three tickets opens three folios.
 */
@Component
@RequiredArgsConstructor
public class TicketConfirmedFolioConsumer {

    private final TicketFolioOpeningService folioOpeningService;

    @ApplicationModuleListener
    public void onTicketConfirmed(MlandTicketConfirmedEvent event) {
        if (event == null || event.ticketReference() == null) {
            return;
        }
        folioOpeningService.openTicketFolio(event.groupReference(), event.ticketReference(), event.finalPriceVnd(),
                event.collectedAtBookingVnd(), event.remainingVnd(), event.confirmedAt());
    }
}
