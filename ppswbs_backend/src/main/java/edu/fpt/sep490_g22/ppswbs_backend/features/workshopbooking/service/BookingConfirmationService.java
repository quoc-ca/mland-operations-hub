package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingTicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingSessionCommitment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingTicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketDeliveryStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.Instant;

/** Persists only ticket identities and entitlements returned by Workshop Operations. */
@Service
@RequiredArgsConstructor
public class BookingConfirmationService {

    private final WorkshopOperationsAdapter operationsAdapter;
    private final BookingTicketRepository ticketRepository;
    private final BookingTicketSessionEntitlementRepository entitlementRepository;

    public ConfirmedTicketProjection projectConfirmedTickets(WorkshopBooking booking,
                                                              List<BookingSessionCommitment> commitments) {
        DeliverySafeTicketSet projection = operationsAdapter.getBookingTicketProjection(booking.getBookingCode());
        if (projection == null || projection.tickets() == null
                || projection.tickets().size() != booking.getParticipantCount()
                || commitments == null || commitments.isEmpty()) {
            throw new BookingConflictException("Delivery-safe ticket projection is incomplete");
        }

        Set<String> ticketCodes = new HashSet<>();
        Set<String> qrReferences = new HashSet<>();
        Instant workshopEndsAt = null;
        Set<String> expectedSessions = commitments.stream()
                .map(BookingSessionCommitment::getSessionReference).collect(java.util.stream.Collectors.toSet());
        for (DeliverySafeTicket safeTicket : projection.tickets()) {
            if (safeTicket == null || safeTicket.ticketCode() == null || safeTicket.ticketCode().isBlank()
                    || safeTicket.ticketReference() == null
                    || safeTicket.qrReference() == null || safeTicket.qrReference().isBlank()
                    || safeTicket.sessionEntitlements() == null
                    || safeTicket.sessionEntitlements().size() != commitments.size()
                    || safeTicket.sessionEntitlements().stream().anyMatch(entitlement -> entitlement == null
                    || entitlement.sessionReference() == null || entitlement.locationReference() == null
                    || entitlement.scheduledStart() == null || entitlement.scheduledEnd() == null
                    || !entitlement.scheduledEnd().isAfter(entitlement.scheduledStart()))
                    || safeTicket.sessionEntitlements().stream().map(e -> e.sessionReference().toString()).distinct().count()
                    != commitments.size()
                    || !safeTicket.sessionEntitlements().stream().map(e -> e.sessionReference().toString()).collect(java.util.stream.Collectors.toSet())
                    .equals(expectedSessions)
                    || !ticketCodes.add(safeTicket.ticketCode())
                    || !qrReferences.add(safeTicket.qrReference())) {
                throw new BookingConflictException("Delivery-safe ticket projection contains invalid identities or entitlements");
            }
            for (var entitlement : safeTicket.sessionEntitlements()) {
                if (workshopEndsAt == null || entitlement.scheduledEnd().isAfter(workshopEndsAt)) {
                    workshopEndsAt = entitlement.scheduledEnd();
                }
            }
        }

        List<String> renderedTicketCodes = new ArrayList<>(projection.tickets().size());
        for (DeliverySafeTicket safeTicket : projection.tickets()) {
            BookingTicket ticket = ticketRepository.save(BookingTicket.builder()
                    .booking(booking)
                    .ticketCode(safeTicket.ticketCode())
                    .qrReference(safeTicket.qrReference())
                    .deliveryStatus(TicketDeliveryStatus.PENDING)
                    .build());
            renderedTicketCodes.add(ticket.getTicketCode());

            for (var entitlement : safeTicket.sessionEntitlements()) {
                BookingSessionCommitment commitment = commitments.stream()
                        .filter(c -> c.getSessionReference().equals(entitlement.sessionReference().toString()))
                        .findFirst().orElseThrow(() -> new BookingConflictException(
                                "Operations returned an entitlement for an unexpected session"));
                entitlementRepository.save(BookingTicketSessionEntitlement.builder()
                        .bookingTicket(ticket)
                        .sessionCommitment(commitment)
                        .sessionReference(entitlement.sessionReference().toString())
                        .build());
            }
        }
        return new ConfirmedTicketProjection(List.copyOf(renderedTicketCodes), workshopEndsAt);
    }

    public record ConfirmedTicketProjection(List<String> ticketCodes, Instant workshopEndsAt) { }
}
