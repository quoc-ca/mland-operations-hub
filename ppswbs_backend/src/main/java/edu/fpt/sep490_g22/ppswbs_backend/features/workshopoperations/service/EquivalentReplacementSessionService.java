package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.EquivalentReplacementSessions;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.FindEquivalentReplacementSessionsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 003.3 T112 / FR-043: finds every session that could replace one cancelled session of a ticket.
 * <p>
 * A candidate is valid when it is published, at the cancelled session's branch, starts after now (the cancellation
 * moment), has a seat and a unit of reschedule quota left, is not already one of the ticket's sessions, and the
 * ticket's session set with the candidate in place of the cancelled session keeps the package rules (order, gaps,
 * branch) and the same position, so the ticket's other session stays exactly where it is. Read only: nothing is held.
 */
@Service
@RequiredArgsConstructor
public class EquivalentReplacementSessionService {

    private static final ZoneId WORKSHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final WorkshopSessionRepository sessionRepository;
    private final WorkshopPackageReservationPolicy packageReservationPolicy;
    private final TicketSessionReplacementService replacementService;

    @Transactional(readOnly = true)
    public EquivalentReplacementSessions find(FindEquivalentReplacementSessionsQuery query) {
        AttendanceTicket ticket = ticketRepository.findByReference(query.ticketReference().toString()).orElse(null);
        if (ticket == null || ticket.getValidityState() != TicketValidityState.ACTIVE) {
            return EquivalentReplacementSessions.none();
        }
        List<WorkshopSession> sources = entitlementRepository.findByTicketId(ticket.getId()).stream()
                .map(TicketSessionEntitlement::getSession)
                .sorted(sessionOrder())
                .toList();
        String cancelledReference = query.cancelledSessionReference().toString();
        int cancelledIndex = -1;
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).getReference().equals(cancelledReference)) {
                cancelledIndex = i;
            }
        }
        if (cancelledIndex < 0) {
            return EquivalentReplacementSessions.none();
        }
        WorkshopSession cancelled = sources.get(cancelledIndex);
        Instant now = Instant.now();
        LocalDate fromDate = now.atZone(WORKSHOP_ZONE).toLocalDate().minusDays(1);

        List<EquivalentReplacementSessions.Candidate> found = new ArrayList<>();
        for (WorkshopSession candidate : sessionRepository
                .findByLocationIdAndStateAndWorkshopDateGreaterThanEqualOrderByScheduledStartAtAsc(
                        cancelled.getLocation().getId(), SessionState.PUBLISHED, fromDate)) {
            if (candidate.getScheduledStartAt() == null || !candidate.getScheduledStartAt().isAfter(now)
                    || sources.stream().anyMatch(source -> source.getId().equals(candidate.getId()))) {
                continue;
            }
            List<WorkshopSession> resulting = new ArrayList<>(sources);
            resulting.set(cancelledIndex, candidate);
            List<WorkshopSession> sorted = resulting.stream().sorted(sessionOrder()).toList();
            // The replacement pairs sessions by position, so the candidate must keep the cancelled session's place.
            if (!sorted.get(cancelledIndex).getId().equals(candidate.getId())
                    || !packageReservationPolicy.isValidTicketReplacement(ticket.getBookingReference(), sorted)) {
                continue;
            }
            if (replacementService.remainingCapacity(candidate, now) < 1 || candidate.remainingRescheduleQuota() < 1) {
                continue;
            }
            found.add(new EquivalentReplacementSessions.Candidate(UUID.fromString(candidate.getReference()),
                    candidate.getScheduledStartAt(), candidate.getScheduledEndAt()));
        }
        return new EquivalentReplacementSessions(found);
    }

    private static Comparator<WorkshopSession> sessionOrder() {
        return Comparator.comparing(WorkshopSession::getScheduledStartAt).thenComparing(WorkshopSession::getReference);
    }
}
