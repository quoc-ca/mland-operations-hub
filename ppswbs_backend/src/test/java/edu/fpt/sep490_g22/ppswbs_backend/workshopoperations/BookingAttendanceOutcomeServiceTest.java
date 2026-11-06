package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.BookingAttendanceOutcomeService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BookingAttendanceOutcomeServiceTest {
    private final TicketSessionEntitlementRepository repository = mock(TicketSessionEntitlementRepository.class);
    private final BookingAttendanceOutcomeService service = new BookingAttendanceOutcomeService(repository);
    private final Instant closedNow = Instant.parse("2026-11-05T12:00:00Z");

    @Test void reportsAllNoShowOnlyAfterWindowCloses() {
        when(repository.findActiveByBookingReference("BK-1")).thenReturn(List.of(item(AttendanceState.NO_SHOW), item(AttendanceState.NO_SHOW)));
        assertThat(service.evaluate("BK-1", closedNow).outcome().outcome()).isEqualTo(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome.Outcome.NO_SHOW);
    }

    @Test void correctionWithinTheWindowChangesTheFinalOutcome() {
        TicketSessionEntitlement corrected = item(AttendanceState.NO_SHOW);
        when(repository.findActiveByBookingReference("BK-2")).thenReturn(List.of(item(AttendanceState.NO_SHOW), corrected));
        assertThat(service.evaluate("BK-2", Instant.parse("2026-11-05T11:59:59Z")).outcome().terminal()).isFalse();

        corrected.setAttendanceState(AttendanceState.CHECKED_IN);
        assertThat(service.evaluate("BK-2", closedNow).outcome().outcome()).isEqualTo(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome.Outcome.PARTIAL);
    }

    @Test void reportsAttendedWhenEveryTicketCheckedIn() {
        when(repository.findActiveByBookingReference("BK-3")).thenReturn(List.of(item(AttendanceState.CHECKED_IN), item(AttendanceState.CHECKED_IN)));
        assertThat(service.evaluate("BK-3", closedNow).outcome().outcome()).isEqualTo(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome.Outcome.ATTENDED);
    }

    @Test void remainsPendingInsideCorrectionWindow() {
        when(repository.findActiveByBookingReference("BK-4")).thenReturn(List.of(item(AttendanceState.NO_SHOW)));
        assertThat(service.evaluate("BK-4", Instant.parse("2026-11-05T11:59:59Z")).outcome().terminal()).isFalse();
    }

    @Test void reportsPartialWhenTicketsAttendSomeRequiredSessionsOnly() {
        // One ticket, two required sessions: attended the first, no-show on the second.
        when(repository.findActiveByBookingReference("BK-5")).thenReturn(List.of(
                item(AttendanceState.CHECKED_IN, Instant.parse("2026-11-03T12:00:00Z")),
                item(AttendanceState.NO_SHOW, Instant.parse("2026-11-04T12:00:00Z"))));

        var evaluation = service.evaluate("BK-5", closedNow);

        assertThat(evaluation.outcome().terminal()).isTrue();
        assertThat(evaluation.outcome().outcome()).isEqualTo(
                edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome.Outcome.PARTIAL);
        // The correction window runs from the latest session finalization.
        assertThat(evaluation.finalizedAt()).isEqualTo(Instant.parse("2026-11-05T12:00:00Z"));
    }

    @Test void remainsPendingWhileAnyRequiredSessionIsNotFinalized() {
        TicketSessionEntitlement open = item(AttendanceState.NO_SHOW);
        open.getSession().setState(SessionState.IN_PROGRESS);
        when(repository.findActiveByBookingReference("BK-6")).thenReturn(List.of(item(AttendanceState.NO_SHOW), open));

        assertThat(service.evaluate("BK-6", closedNow.plusSeconds(86_400)).outcome().terminal()).isFalse();
    }

    private TicketSessionEntitlement item(AttendanceState state) {
        return item(state, Instant.parse("2026-11-04T12:00:00Z"));
    }

    private TicketSessionEntitlement item(AttendanceState state, Instant finalizedAt) {
        WorkshopSession session = new WorkshopSession();
        session.setState(SessionState.ATTENDANCE_FINALIZED);
        session.setAttendanceFinalizedAt(finalizedAt);
        TicketSessionEntitlement item = new TicketSessionEntitlement();
        item.setSession(session); item.setAttendanceState(state); return item;
    }
}
