package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceActionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketQrGenerationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CheckInService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopCheckInNotOpenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T126: the CheckInService guard before, after and while a session's check-in window is open. */
class CheckInServiceTest {

    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final AttendanceTicketRepository tickets = mock(AttendanceTicketRepository.class);
    private final WorkshopOperationsAuthorizationService authorization = mock(WorkshopOperationsAuthorizationService.class);
    private final CheckInService service = new CheckInService(sessions, tickets, mock(TicketQrGenerationRepository.class),
            mock(TicketSessionEntitlementRepository.class), mock(AttendanceActionRepository.class),
            mock(OperationsAuditEventRepository.class), authorization);

    @BeforeEach
    void actor() {
        when(authorization.requireStaffSessionAssignment(anyString(), anyLong())).thenReturn(
                new AccountSummary(101L, ActorRole.STAFF, ActorStatus.ACTIVE, "s@x.test", Instant.now(), Instant.now()));
        // Past the window the ticket lookup is reached; an unknown ticket proves the window guard let the request through.
        when(tickets.findByTicketCode(anyString())).thenReturn(Optional.empty());
    }

    private WorkshopSession session(SessionState state, Instant checkInOpensAt) {
        WorkshopSession session = WorkshopSession.builder().reference("S-1").state(state).checkInOpensAt(checkInOpensAt).build();
        session.setId(7L);
        when(sessions.findByReference("S-1")).thenReturn(Optional.of(session));
        return session;
    }

    private static CheckInRequest request() {
        return CheckInRequest.builder().ticketCode("TK-1").build();
    }

    @Test
    void beforeCheckInOpensAtIsCheckInNotOpenCarryingTheInstant() {
        Instant opensAt = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        session(SessionState.PUBLISHED, opensAt);

        assertThatThrownBy(() -> service.checkIn("Bearer x", "S-1", request()))
                .isInstanceOfSatisfying(WorkshopCheckInNotOpenException.class, e -> {
                    assertThat(e.getCheckInOpensAt()).isEqualTo(opensAt);
                    assertThat(e.getMessage()).contains("check-in opens at");
                });
    }

    @Test
    void afterCheckInOpensAtTheRequestPassesTheWindowGuard() {
        session(SessionState.PUBLISHED, Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> service.checkIn("Bearer x", "S-1", request())).isInstanceOf(WorkshopNotFoundException.class);
    }

    @Test
    void exactlyAtCheckInOpensAtIsOpen() {
        // now is never before an instant that has just passed; a null instant means no window restriction.
        session(SessionState.SALES_PAUSED, Instant.now().minusMillis(1));
        assertThatThrownBy(() -> service.checkIn("Bearer x", "S-1", request())).isInstanceOf(WorkshopNotFoundException.class);

        session(SessionState.PUBLISHED, null);
        assertThatThrownBy(() -> service.checkIn("Bearer x", "S-1", request())).isInstanceOf(WorkshopNotFoundException.class);
    }

    @Test
    void aSessionNotOpenForCheckInStaysALifecycleErrorEvenBeforeTheWindow() {
        session(SessionState.CANCELLED, Instant.now().plus(2, ChronoUnit.HOURS));

        assertThatThrownBy(() -> service.checkIn("Bearer x", "S-1", request()))
                .isInstanceOf(WorkshopLifecycleException.class)
                .isNotInstanceOf(WorkshopCheckInNotOpenException.class);
    }
}
