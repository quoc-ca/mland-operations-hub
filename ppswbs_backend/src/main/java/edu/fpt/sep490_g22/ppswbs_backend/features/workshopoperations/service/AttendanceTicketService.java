package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service managing individual Attendance Tickets, opaque QR references,
 * multi-session entitlements (e.g. Silverclay packages), and privacy-safe projections.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceTicketService {

    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final CapacityHoldRepository capacityHoldRepository;

    /**
     * Issues individual tickets for confirmed holds of a booking.
     * N participants correspond to N individual tickets (no one-ticket-per-booking constraint).
     * Multi-session packages (e.g. Silverclay) create entitlements on every required session for each ticket.
     * Tickets contain no participant identity or contact PII.
     */
    @Transactional
    public List<AttendanceTicket> issueTicketsForBooking(String bookingReference, List<UUID> holdReferences) {
        List<AttendanceTicket> existingTickets = ticketRepository.findByBookingReference(bookingReference);
        if (!existingTickets.isEmpty()) {
            log.info("Tickets already exist for bookingReference={}, returning {} existing tickets",
                    bookingReference, existingTickets.size());
            return existingTickets;
        }

        List<String> holdRefStrings = holdReferences.stream().map(UUID::toString).sorted().toList();
        List<CapacityHold> holds = capacityHoldRepository.findByReferenceInWithLock(holdRefStrings);
        if (holds.isEmpty()) {
            log.warn("Cannot issue tickets: no holds found for references {}", holdReferences);
            return List.of();
        }

        int participantCount = holds.getFirst().getRequestedTicketCount();
        List<AttendanceTicket> createdTickets = new ArrayList<>();

        for (int i = 1; i <= participantCount; i++) {
            String ticketCode = generateUniqueTicketCode();
            String qrRef = generateUniqueQrReference();

            AttendanceTicket ticket = AttendanceTicket.builder()
                    .reference(UUID.randomUUID().toString())
                    .bookingReference(bookingReference)
                    .ticketSequence(i)
                    .ticketCode(ticketCode)
                    .qrReference(qrRef)
                    .validityState(TicketValidityState.ACTIVE)
                    .build();

            ticket = ticketRepository.save(ticket);
            createdTickets.add(ticket);
        }

        // Each ticket is entitled to every session in the booking commitment set
        // (e.g. Silverclay has entitlements across both session 1 and session 2)
        for (CapacityHold hold : holds) {
            WorkshopSession session = hold.getSession();
            for (AttendanceTicket ticket : createdTickets) {
                TicketSessionEntitlement entitlement = TicketSessionEntitlement.builder()
                        .ticket(ticket)
                        .session(session)
                        .attendanceState(AttendanceState.NOT_CHECKED_IN)
                        .build();
                entitlementRepository.save(entitlement);
            }
        }

        log.info("Issued {} attendance tickets across {} sessions for bookingReference={}",
                createdTickets.size(), holds.size(), bookingReference);
        return createdTickets;
    }

    /**
     * Returns a delivery-safe ticket projection containing ticket identity, ticket code,
     * and entitled sessions with location and schedule.
     * Excludes attendance details, Staff/audit data, and buyer contact PII.
     */
    @Transactional(readOnly = true)
    public DeliverySafeTicketSet getBookingTicketProjection(String bookingReference) {
        List<AttendanceTicket> tickets = ticketRepository.findByBookingReference(bookingReference);
        List<DeliverySafeTicket> projections = new ArrayList<>();

        for (AttendanceTicket ticket : tickets) {
            List<TicketSessionEntitlement> entitlements = entitlementRepository.findByTicketId(ticket.getId());
            List<SessionEntitlementProjection> sessionProjections = entitlements.stream()
                    .map(e -> new SessionEntitlementProjection(
                            e.getSession().getLocation() != null ? e.getSession().getLocation().getId() : null,
                            e.getSession().getScheduledStartAt(),
                            e.getSession().getScheduledEndAt()
                    ))
                    .toList();

            projections.add(new DeliverySafeTicket(
                    UUID.fromString(ticket.getReference()),
                    ticket.getTicketCode(),
                    sessionProjections
            ));
        }

        return new DeliverySafeTicketSet(projections);
    }

    private String generateUniqueTicketCode() {
        String code;
        do {
            code = "TK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (ticketRepository.existsByTicketCode(code));
        return code;
    }

    private String generateUniqueQrReference() {
        String qr;
        do {
            qr = "QR-" + UUID.randomUUID().toString();
        } while (ticketRepository.existsByQrReference(qr));
        return qr;
    }
}
