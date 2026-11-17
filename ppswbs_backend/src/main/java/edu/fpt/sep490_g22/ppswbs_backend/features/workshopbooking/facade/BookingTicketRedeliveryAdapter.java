package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.BookingTicketRedeliveryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bridges the neutral redelivery port to the Booking public facade implementation. */
@Component
@RequiredArgsConstructor
public class BookingTicketRedeliveryAdapter implements BookingTicketRedeliveryPort {
    private final WorkshopBookingFacade workshopBookingFacade;

    @Override
    public RedeliveryOutcome request(RedeliveryCommand command) {
        TicketRedeliveryOutcome outcome = workshopBookingFacade.requestBookingTicketRedelivery(
                new RequestBookingTicketRedeliveryCommand(
                        command.bookingReference(),
                        command.activeUnusedTicketReferences(),
                        command.operationReference(),
                        command.correlationReference()
                )
        );
        return new RedeliveryOutcome(
                outcome.status() == BookingOutcomeStatus.ACCEPTED
                        ? RedeliveryOutcome.Status.ACCEPTED
                        : RedeliveryOutcome.Status.REJECTED,
                outcome.reason() == null ? null : outcome.reason().name()
        );
    }
}
