package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingAttendanceOutcomeService {
    private final TicketSessionEntitlementRepository entitlements;

    public Evaluation evaluate(String bookingReference, Instant now) {
        if (bookingReference == null || bookingReference.isBlank()) return Evaluation.pending();
        return evaluate(entitlements.findActiveByBookingReference(bookingReference), now);
    }

    /**
     * The attendance outcome for one ticket alone (003.2 T092, 003.3 FR-032).
     * Anchored 24 hours after the latest finalization among that ticket's own sessions.
     */
    public Evaluation evaluateTicket(String ticketReference, Instant now) {
        if (ticketReference == null || ticketReference.isBlank()) return Evaluation.pending();
        return evaluate(entitlements.findActiveByTicketReference(ticketReference), now);
    }

    private Evaluation evaluate(List<TicketSessionEntitlement> items, Instant now) {
        if (items.isEmpty()) return Evaluation.pending();
        if (items.stream().anyMatch(item -> item.getSession() == null
                || item.getSession().getState() != SessionState.ATTENDANCE_FINALIZED
                || item.getSession().getAttendanceFinalizedAt() == null)) {
            boolean anyCheckedIn = items.stream().anyMatch(item -> item.getAttendanceState() == AttendanceState.CHECKED_IN);
            return anyCheckedIn
                    ? new Evaluation(TicketAttendanceOutcome.pending(null, TicketAttendanceOutcome.Live.CHECKED_IN), null)
                    : Evaluation.pending();
        }
        var closeAt = AttendanceCorrectionWindowPolicy.closesAt(items.stream()
                .map(item -> item.getSession().getAttendanceFinalizedAt()).toList());
        if (closeAt.isEmpty()) {
            return Evaluation.pending();
        }
        Instant windowClosesAt = closeAt.get();
        boolean allNoShow = items.stream().allMatch(item -> item.getAttendanceState() == AttendanceState.NO_SHOW);
        boolean allAttended = items.stream().allMatch(item -> item.getAttendanceState() == AttendanceState.CHECKED_IN);
        if (now.isBefore(windowClosesAt)) {
            boolean anyCheckedIn = items.stream().anyMatch(item -> item.getAttendanceState() == AttendanceState.CHECKED_IN);
            TicketAttendanceOutcome.Live live = allNoShow ? TicketAttendanceOutcome.Live.NO_SHOW_PENDING
                    : allAttended ? TicketAttendanceOutcome.Live.ATTENDED
                    : anyCheckedIn ? TicketAttendanceOutcome.Live.CHECKED_IN : null;
            return new Evaluation(TicketAttendanceOutcome.pending(windowClosesAt, live), windowClosesAt);
        }
        TicketAttendanceOutcome.Outcome outcome = allNoShow ? TicketAttendanceOutcome.Outcome.NO_SHOW
                : allAttended ? TicketAttendanceOutcome.Outcome.ATTENDED : TicketAttendanceOutcome.Outcome.PARTIAL;
        return new Evaluation(new TicketAttendanceOutcome(true, outcome, windowClosesAt), windowClosesAt);
    }

    public record Evaluation(TicketAttendanceOutcome outcome, Instant finalizedAt) {
        public static Evaluation pending() { return new Evaluation(TicketAttendanceOutcome.pending(), null); }
    }
}
