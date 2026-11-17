package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.BookingAttendanceOutcomeService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BookingAttendanceOutcomeServiceTest {
    private final TicketSessionEntitlementRepository repository = mock(TicketSessionEntitlementRepository.class);
    private final BookingAttendanceOutcomeService service = new BookingAttendanceOutcomeService(repository);
    private final Instant sessionFinalizedAt = Instant.parse("2026-11-04T12:00:00Z");
    private final Instant windowClosesAt = sessionFinalizedAt.plusSeconds(24 * 3600); // 2026-11-05T12:00:00Z
    private final Instant closedNow = windowClosesAt;

    @Test
    void reportsAllNoShowOnlyAfterWindowCloses() {
        when(repository.findActiveByTicketReference("TKT-1"))
                .thenReturn(List.of(item(AttendanceState.NO_SHOW), item(AttendanceState.NO_SHOW)));

        var evaluation = service.evaluateTicket("TKT-1", closedNow);
        assertThat(evaluation.outcome().terminal()).isTrue();
        assertThat(evaluation.outcome().outcome()).isEqualTo(TicketAttendanceOutcome.Outcome.NO_SHOW);
        assertThat(evaluation.outcome().windowClosesAt()).isEqualTo(windowClosesAt);
    }

    @Test
    void correctionWithinTheWindowChangesTheFinalOutcome() {
        TicketSessionEntitlement corrected = item(AttendanceState.NO_SHOW);
        when(repository.findActiveByTicketReference("TKT-2"))
                .thenReturn(List.of(item(AttendanceState.NO_SHOW), corrected));

        // Before window close: terminal is false, but windowClosesAt is available
        var beforeClose = service.evaluateTicket("TKT-2", Instant.parse("2026-11-05T11:59:59Z"));
        assertThat(beforeClose.outcome().terminal()).isFalse();
        assertThat(beforeClose.outcome().outcome()).isNull();
        assertThat(beforeClose.outcome().windowClosesAt()).isEqualTo(windowClosesAt);

        // Staff corrects to CHECKED_IN within window
        corrected.setAttendanceState(AttendanceState.CHECKED_IN);
        var afterClose = service.evaluateTicket("TKT-2", closedNow);
        assertThat(afterClose.outcome().terminal()).isTrue();
        assertThat(afterClose.outcome().outcome()).isEqualTo(TicketAttendanceOutcome.Outcome.PARTIAL);
        assertThat(afterClose.outcome().windowClosesAt()).isEqualTo(windowClosesAt);
    }

    @Test
    void reportsAttendedWhenEveryTicketCheckedIn() {
        when(repository.findActiveByTicketReference("TKT-3"))
                .thenReturn(List.of(item(AttendanceState.CHECKED_IN), item(AttendanceState.CHECKED_IN)));

        var evaluation = service.evaluateTicket("TKT-3", closedNow);
        assertThat(evaluation.outcome().terminal()).isTrue();
        assertThat(evaluation.outcome().outcome()).isEqualTo(TicketAttendanceOutcome.Outcome.ATTENDED);
        assertThat(evaluation.outcome().windowClosesAt()).isEqualTo(windowClosesAt);
    }

    @Test
    void remainsPendingInsideCorrectionWindowWithWindowClosesAtPopulated() {
        when(repository.findActiveByTicketReference("TKT-4"))
                .thenReturn(List.of(item(AttendanceState.NO_SHOW)));

        var evaluation = service.evaluateTicket("TKT-4", Instant.parse("2026-11-05T11:59:59Z"));
        assertThat(evaluation.outcome().terminal()).isFalse();
        assertThat(evaluation.outcome().outcome()).isNull();
        assertThat(evaluation.outcome().windowClosesAt()).isEqualTo(windowClosesAt);
    }

    @Test
    void reportsPartialWhenTicketsAttendSomeRequiredSessionsOnly() {
        // One ticket, two required sessions: attended the first, no-show on the second.
        when(repository.findActiveByTicketReference("TKT-5")).thenReturn(List.of(
                item(AttendanceState.CHECKED_IN, Instant.parse("2026-11-03T12:00:00Z")),
                item(AttendanceState.NO_SHOW, Instant.parse("2026-11-04T12:00:00Z"))));

        var evaluation = service.evaluateTicket("TKT-5", closedNow);

        assertThat(evaluation.outcome().terminal()).isTrue();
        assertThat(evaluation.outcome().outcome()).isEqualTo(TicketAttendanceOutcome.Outcome.PARTIAL);
        // Anchored 24 hours after the latest session finalization among that ticket's own sessions
        assertThat(evaluation.outcome().windowClosesAt()).isEqualTo(Instant.parse("2026-11-05T12:00:00Z"));
        assertThat(evaluation.finalizedAt()).isEqualTo(Instant.parse("2026-11-05T12:00:00Z"));
    }

    @Test
    void remainsPendingWhileAnyRequiredSessionIsNotFinalized() {
        TicketSessionEntitlement open = item(AttendanceState.NO_SHOW);
        open.getSession().setState(SessionState.IN_PROGRESS);
        when(repository.findActiveByTicketReference("TKT-6")).thenReturn(List.of(item(AttendanceState.NO_SHOW), open));

        var evaluation = service.evaluateTicket("TKT-6", closedNow.plusSeconds(86_400));
        assertThat(evaluation.outcome().terminal()).isFalse();
        assertThat(evaluation.outcome().windowClosesAt()).isNull();
    }

    @Test
    void showsCheckedInLiveWhileTheSessionIsStillOpen() {
        TicketSessionEntitlement open = item(AttendanceState.CHECKED_IN);
        open.getSession().setState(SessionState.IN_PROGRESS);
        when(repository.findActiveByTicketReference("TKT-7")).thenReturn(List.of(open));

        var outcome = service.evaluateTicket("TKT-7", sessionFinalizedAt).outcome();
        assertThat(outcome.terminal()).isFalse();
        assertThat(outcome.live()).isEqualTo(TicketAttendanceOutcome.Live.CHECKED_IN);
    }

    @Test
    void showsAttendedAndNoShowPendingLiveInsideTheCorrectionWindow() {
        Instant inside = Instant.parse("2026-11-05T11:59:59Z");
        when(repository.findActiveByTicketReference("TKT-8")).thenReturn(List.of(item(AttendanceState.CHECKED_IN)));
        when(repository.findActiveByTicketReference("TKT-9")).thenReturn(List.of(item(AttendanceState.NO_SHOW)));

        assertThat(service.evaluateTicket("TKT-8", inside).outcome().live()).isEqualTo(TicketAttendanceOutcome.Live.ATTENDED);
        assertThat(service.evaluateTicket("TKT-9", inside).outcome().live()).isEqualTo(TicketAttendanceOutcome.Live.NO_SHOW_PENDING);
    }

    @Test
    void showsNothingWhenNobodyCheckedInYet() {
        TicketSessionEntitlement open = item(AttendanceState.NOT_CHECKED_IN);
        open.getSession().setState(SessionState.IN_PROGRESS);
        when(repository.findActiveByTicketReference("TKT-10")).thenReturn(List.of(open));

        assertThat(service.evaluateTicket("TKT-10", sessionFinalizedAt).outcome().live()).isNull();
    }

    private TicketSessionEntitlement item(AttendanceState state) {
        return item(state, sessionFinalizedAt);
    }

    private TicketSessionEntitlement item(AttendanceState state, Instant finalizedAt) {
        WorkshopSession session = new WorkshopSession();
        session.setState(SessionState.ATTENDANCE_FINALIZED);
        session.setAttendanceFinalizedAt(finalizedAt);
        TicketSessionEntitlement item = new TicketSessionEntitlement();
        item.setSession(session);
        item.setAttendanceState(state);
        return item;
    }
}
