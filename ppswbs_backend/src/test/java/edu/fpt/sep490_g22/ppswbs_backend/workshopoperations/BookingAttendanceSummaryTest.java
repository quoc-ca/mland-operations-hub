package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingSettlementReadinessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacadeImpl;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceCorrectionWindowPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What a customer may be told about attendance: available as soon as the sessions are finalized, before the outcome is final. */
class BookingAttendanceSummaryTest {

    private static final String BOOKING = "BK-1";

    private TicketSessionEntitlementRepository entitlements;
    private WorkshopOperationsFacadeImpl facade;

    @BeforeEach
    void setUp() {
        entitlements = mock(TicketSessionEntitlementRepository.class);
        facade = Mockito.mock(WorkshopOperationsFacadeImpl.class, Mockito.CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(facade, "entitlementRepository", entitlements);
    }

    private static TicketSessionEntitlement entitlement(SessionState state, Instant finalizedAt, AttendanceState attendance) {
        WorkshopSession session = new WorkshopSession();
        session.setState(state);
        session.setAttendanceFinalizedAt(finalizedAt);
        TicketSessionEntitlement item = new TicketSessionEntitlement();
        item.setSession(session);
        item.setAttendanceState(attendance);
        return item;
    }

    private BookingAttendanceSummary summary() {
        return facade.getBookingAttendanceSummary(new BookingSettlementReadinessQuery(BOOKING));
    }

    @Test
    void nothingIsReportedWhileTheSessionIsNotFinalized() {
        when(entitlements.findActiveByBookingReference(BOOKING)).thenReturn(List.of(
                entitlement(SessionState.IN_PROGRESS, null, AttendanceState.NOT_CHECKED_IN)));

        assertThat(summary()).isEqualTo(BookingAttendanceSummary.notFinalized());
    }

    @Test
    void aBookingWithNoActiveTicketsReportsNothing() {
        when(entitlements.findActiveByBookingReference(BOOKING)).thenReturn(List.of());

        assertThat(summary().sessionsFinalized()).isFalse();
    }

    @Test
    void aNoShowIsReportedRightAfterFinalizationWhileCorrectionIsStillOpen() {
        when(entitlements.findActiveByBookingReference(BOOKING)).thenReturn(List.of(
                entitlement(SessionState.ATTENDANCE_FINALIZED, Instant.now().minusSeconds(60), AttendanceState.NO_SHOW)));

        BookingAttendanceSummary result = summary();

        assertThat(result.sessionsFinalized()).isTrue();
        assertThat(result.hasNoShow()).isTrue();
        assertThat(result.correctionWindowClosed()).isFalse();
    }

    @Test
    void aNoShowBecomesFinalAfterTheCorrectionWindow() {
        Instant longAgo = Instant.now().minus(AttendanceCorrectionWindowPolicy.WINDOW).minusSeconds(60);
        when(entitlements.findActiveByBookingReference(BOOKING)).thenReturn(List.of(
                entitlement(SessionState.ATTENDANCE_FINALIZED, longAgo, AttendanceState.NO_SHOW)));

        assertThat(summary().correctionWindowClosed()).isTrue();
    }

    @Test
    void aCheckedInTicketIsAttended() {
        when(entitlements.findActiveByBookingReference(BOOKING)).thenReturn(List.of(
                entitlement(SessionState.ATTENDANCE_FINALIZED, Instant.now().minusSeconds(60), AttendanceState.CHECKED_IN)));

        BookingAttendanceSummary result = summary();

        assertThat(result.sessionsFinalized()).isTrue();
        assertThat(result.hasNoShow()).isFalse();
    }
}
