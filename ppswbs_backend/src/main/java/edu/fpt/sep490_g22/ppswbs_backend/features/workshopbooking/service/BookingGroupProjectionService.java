package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketRecipientRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingGroupResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingGroupView;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLineSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketRecipient;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the buyer's view of a booking group (003.1 FR-042, FR-049). The current schedule of a confirmed ticket comes
 * from Workshop Operations, so a replaced session shows its new time; before confirmation, or when Operations cannot
 * answer, the booking-time schedule of the line is shown instead. Attendance is read per ticket.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingGroupProjectionService {

    private final BookingGroupLineRepository lines;
    private final TicketBookingRepository tickets;
    private final TicketPriceSnapshotRepository snapshots;
    private final TicketRecipientRepository recipients;
    private final WorkshopOperationsAdapter operations;

    /** @param forOwningMember adds each ticket's opaque reference; only the owning Member may receive it. */
    @Transactional(readOnly = true)
    public BookingGroupView project(BookingGroup group, boolean forOwningMember) {
        List<BookingGroupLine> groupLines = lines.findByGroupIdOrderByLineIndexAsc(group.getId());
        List<BookingGroupResponse.GroupLine> lineViews = new ArrayList<>();
        Map<Integer, BookingGroupLine> lineByIndex = new HashMap<>();
        Map<String, DeliverySafeTicket> currentByTicketReference = new HashMap<>();
        for (BookingGroupLine line : groupLines) {
            lineByIndex.put(line.getLineIndex(), line);
            lineViews.add(new BookingGroupResponse.GroupLine(line.getLineIndex(), line.getPackageId(),
                    line.getPackageNameSnapshot(), line.getPricePerParticipantVnd(), line.getTicketCount(),
                    orderedSessions(line).stream().map(s -> uuid(s.getSessionReference())).toList()));
            if (group.getGroupStatus() == BookingStatus.CONFIRMED) {
                currentByTicketReference.putAll(currentTickets(line));
            }
        }

        List<BookingGroupView.TicketView> ticketViews = new ArrayList<>();
        for (TicketBooking ticket : tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.getId())) {
            TicketPriceSnapshot snapshot = snapshots.findByTicketId(ticket.getId()).orElse(null);
            TicketRecipient recipient = recipients.findByTicketId(ticket.getId()).orElse(null);
            boolean confirmed = ticket.getTicketStatus() == BookingStatus.CONFIRMED;
            DeliverySafeTicket current = ticket.getOperationsTicketReference() == null ? null
                    : currentByTicketReference.get(ticket.getOperationsTicketReference());
            ticketViews.add(new BookingGroupView.TicketView(
                    ticket.getTicketCode(),
                    ticket.getLineIndex(),
                    ticket.getParticipantOrdinal(),
                    ticket.getPackageId(),
                    ticket.getPackageNameSnapshot(),
                    ticket.getTicketStatus() == null ? null : ticket.getTicketStatus().name(),
                    ticket.getFinancialStatus() == null ? null : ticket.getFinancialStatus().name(),
                    snapshot == null ? 0 : snapshot.getFinalPriceVnd(),
                    snapshot == null ? 0 : snapshot.getCollectedAtBookingVnd(),
                    snapshot == null || snapshot.getCollectionKind() == null ? null : snapshot.getCollectionKind().name(),
                    snapshot == null ? 0 : snapshot.getRemainingVnd(),
                    recipient == null ? null : EmailMasking.mask(recipient.getRecipientEmail()),
                    confirmed ? Math.max(0, TicketRecipientService.MAX_ONLINE_CHANGES
                            - (recipient == null ? 0 : recipient.getChangeCount())) : null,
                    confirmed ? attendance(ticket) : null,
                    confirmed ? ticket.getQrReference() : null,
                    forOwningMember && confirmed ? ticket.getOperationsTicketReference() : null,
                    sessions(current, lineByIndex.get(ticket.getLineIndex()))));
        }
        return new BookingGroupView(group.getGroupCode(), group.getGroupStatus().name(),
                group.getPaymentMode() == null ? null : group.getPaymentMode().name(), group.getGroupCollectionVnd(),
                group.getHoldExpiresAt(), group.getPaymentDeadlineAt(), group.getCreatedAt(), group.getConfirmedAt(),
                List.copyOf(lineViews), List.copyOf(ticketViews));
    }

    private Map<String, DeliverySafeTicket> currentTickets(BookingGroupLine line) {
        Map<String, DeliverySafeTicket> result = new HashMap<>();
        try {
            DeliverySafeTicketSet set = operations.getCurrentEntitlementProjection(line.getLineReference());
            if (set == null || set.tickets() == null) return result;
            for (DeliverySafeTicket ticket : set.tickets()) {
                if (ticket != null && ticket.ticketReference() != null) {
                    result.put(ticket.ticketReference().toString(), ticket);
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Current schedule unavailable for group line {}: {}", line.getLineReference(),
                    failure.getClass().getSimpleName());
        }
        return result;
    }

    /** ATTENDED, NO_SHOW or PARTIAL once final; null while attendance can still change. */
    private String attendance(TicketBooking ticket) {
        if (ticket.getOperationsTicketReference() == null) return null;
        try {
            TicketAttendanceOutcome outcome = operations.getTicketAttendanceOutcome(ticket.getOperationsTicketReference());
            return outcome != null && outcome.terminal() && outcome.outcome() != null ? outcome.outcome().name() : null;
        } catch (RuntimeException failure) {
            log.warn("Attendance unavailable for ticket {}: {}", ticket.getTicketCode(), failure.getClass().getSimpleName());
            return null;
        }
    }

    private static List<BookingGroupView.SessionView> sessions(DeliverySafeTicket current, BookingGroupLine line) {
        if (current != null && !current.sessionEntitlements().isEmpty()) {
            List<SessionEntitlementProjection> ordered = new ArrayList<>(current.sessionEntitlements());
            ordered.sort(Comparator.comparing(SessionEntitlementProjection::scheduledStart,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            List<BookingGroupView.SessionView> result = new ArrayList<>();
            for (int i = 0; i < ordered.size(); i++) {
                SessionEntitlementProjection s = ordered.get(i);
                result.add(new BookingGroupView.SessionView(s.sessionReference(), s.locationReference(), i + 1,
                        s.scheduledStart(), s.scheduledEnd()));
            }
            return List.copyOf(result);
        }
        if (line == null) return List.of();
        return orderedSessions(line).stream()
                .map(s -> new BookingGroupView.SessionView(uuid(s.getSessionReference()), s.getLocationReference(),
                        s.getOrdinal(), s.getScheduledStart(), null))
                .toList();
    }

    private static List<BookingGroupLineSession> orderedSessions(BookingGroupLine line) {
        return line.getSessions().stream().sorted(Comparator.comparingInt(BookingGroupLineSession::getOrdinal)).toList();
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
