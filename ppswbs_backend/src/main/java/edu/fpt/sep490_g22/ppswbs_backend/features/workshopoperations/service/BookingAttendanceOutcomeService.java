package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome;
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
        List<TicketSessionEntitlement> items = entitlements.findActiveByBookingReference(bookingReference);
        if (items.isEmpty() || items.stream().anyMatch(item -> item.getSession() == null
                || item.getSession().getState() != SessionState.ATTENDANCE_FINALIZED
                || item.getSession().getAttendanceFinalizedAt() == null)) return Evaluation.pending();
        var closeAt = AttendanceCorrectionWindowPolicy.closesAt(items.stream()
                .map(item -> item.getSession().getAttendanceFinalizedAt()).toList());
        if (closeAt.isEmpty() || now.isBefore(closeAt.get())) return Evaluation.pending();
        boolean allNoShow = items.stream().allMatch(item -> item.getAttendanceState() == AttendanceState.NO_SHOW);
        boolean allAttended = items.stream().allMatch(item -> item.getAttendanceState() == AttendanceState.CHECKED_IN);
        BookingAttendanceOutcome.Outcome outcome = allNoShow ? BookingAttendanceOutcome.Outcome.NO_SHOW
                : allAttended ? BookingAttendanceOutcome.Outcome.ATTENDED : BookingAttendanceOutcome.Outcome.PARTIAL;
        return new Evaluation(new BookingAttendanceOutcome(true, outcome), closeAt.get());
    }

    public record Evaluation(BookingAttendanceOutcome outcome, Instant finalizedAt) {
        static Evaluation pending() { return new Evaluation(BookingAttendanceOutcome.pending(), null); }
    }
}
