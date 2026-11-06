package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceActionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AttendanceRosterEntry;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CheckInService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AttendanceRosterTest {
    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final TicketSessionEntitlementRepository entitlements = mock(TicketSessionEntitlementRepository.class);
    private final WorkshopOperationsAuthorizationService authorization = mock(WorkshopOperationsAuthorizationService.class);
    private final CheckInService service = new CheckInService(sessions, mock(AttendanceTicketRepository.class), entitlements,
            mock(AttendanceActionRepository.class), mock(OperationsAuditEventRepository.class), authorization);

    private TicketSessionEntitlement entitlement(String code, String booking, int sequence, AttendanceState state, Instant at) {
        AttendanceTicket ticket = mock(AttendanceTicket.class);
        when(ticket.getTicketCode()).thenReturn(code);
        when(ticket.getBookingReference()).thenReturn(booking);
        when(ticket.getTicketSequence()).thenReturn(sequence);
        TicketSessionEntitlement entitlement = mock(TicketSessionEntitlement.class);
        when(entitlement.getTicket()).thenReturn(ticket);
        when(entitlement.getAttendanceState()).thenReturn(state);
        when(entitlement.getCheckedInAt()).thenReturn(at);
        return entitlement;
    }

    @Test
    void listsEveryValidTicketOfTheSessionWithItsCheckInState() {
        WorkshopSession session = mock(WorkshopSession.class);
        when(session.getId()).thenReturn(12L);
        when(sessions.findByReference("S-1")).thenReturn(Optional.of(session));
        Instant at = Instant.parse("2026-11-06T02:00:00Z");
        // Built first: creating a mock inside another stubbing call is not allowed.
        List<TicketSessionEntitlement> rows = List.of(
                entitlement("TK-1", "BK-A", 1, AttendanceState.CHECKED_IN, at),
                entitlement("TK-2", "BK-A", 2, AttendanceState.NOT_CHECKED_IN, null));
        when(entitlements.findActiveBySessionIdOrdered(12L)).thenReturn(rows);

        List<AttendanceRosterEntry> roster = service.attendanceRoster("Bearer t", "S-1");

        verify(authorization).requireStaffSessionAssignment("Bearer t", 12L);
        assertThat(roster).containsExactly(
                new AttendanceRosterEntry("TK-1", "BK-A", 1, AttendanceState.CHECKED_IN, at),
                new AttendanceRosterEntry("TK-2", "BK-A", 2, AttendanceState.NOT_CHECKED_IN, null));
    }

    @Test
    void anOperatorWithoutAuthorityOverTheSessionSeesNothing() {
        WorkshopSession session = mock(WorkshopSession.class);
        when(session.getId()).thenReturn(12L);
        when(sessions.findByReference("S-1")).thenReturn(Optional.of(session));
        when(authorization.requireStaffSessionAssignment(any(), any())).thenThrow(new WorkshopForbiddenException("not assigned"));

        assertThatThrownBy(() -> service.attendanceRoster("Bearer t", "S-1")).isInstanceOf(WorkshopForbiddenException.class);
        verifyNoInteractions(entitlements);
    }
}
