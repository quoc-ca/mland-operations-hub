package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;

public record DeliverySafeTicketSet(List<DeliverySafeTicket> tickets) {
    public DeliverySafeTicketSet {
        tickets = tickets != null ? List.copyOf(tickets) : List.of();
    }
}
