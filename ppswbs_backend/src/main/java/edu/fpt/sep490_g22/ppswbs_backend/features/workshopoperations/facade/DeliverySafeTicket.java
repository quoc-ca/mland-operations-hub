package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.UUID;

public record DeliverySafeTicket(
    UUID ticketReference,
    String qrReference,
    String ticketCode,
    List<SessionEntitlementProjection> sessionEntitlements
) {
    public DeliverySafeTicket {
        sessionEntitlements = sessionEntitlements != null ? List.copyOf(sessionEntitlements) : List.of();
    }
}
