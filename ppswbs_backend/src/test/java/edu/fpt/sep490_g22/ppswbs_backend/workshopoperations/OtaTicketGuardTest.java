package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSalesChannel;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.2 FR-036 / T096: cancel, session replacement and QR reissue refuse an OTA ticket with one typed failure. */
class OtaTicketGuardTest {

    private final UUID ticketRef = UUID.randomUUID();
    private AttendanceTicketRepository tickets;
    private FacadeCommandIdempotencyManager idempotency;
    private TicketSessionEntitlementRepository entitlements;

    @BeforeEach
    void setUp() {
        tickets = mock(AttendanceTicketRepository.class);
        idempotency = mock(FacadeCommandIdempotencyManager.class);
        entitlements = mock(TicketSessionEntitlementRepository.class);
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ClaimResult.acquired(1L, 1L));
        AttendanceTicket ota = AttendanceTicket.builder().id(5L).reference(ticketRef.toString())
                .bookingReference("OTA-BKG-1").ticketSequence(1).salesChannel(TicketSalesChannel.OTA).build();
        when(tickets.findByReference(ticketRef.toString())).thenReturn(Optional.of(ota));
        when(tickets.findByReferenceWithLock(ticketRef.toString())).thenReturn(Optional.of(ota));
    }

    @Test
    void cancelTicketRefusesAnOtaTicketWithoutTouchingSeats() {
        TicketCancellationService service = new TicketCancellationService(tickets, entitlements,
                mock(TicketEntitlementReleaseRepository.class), mock(CapacityHoldRepository.class),
                mock(WorkshopSessionRepository.class), mock(OperationsAuditEventRepository.class), idempotency,
                mock(WorkshopOtaSnapshotService.class));

        TicketCancellationOutcome outcome = service.cancelTicket(
                new CancelTicketCommand(ticketRef, "case-1", "op-1", "corr-1"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.OTA_TICKET_NOT_SUPPORTED);
        verifyNoInteractions(entitlements);
    }

    @Test
    void reissueQrRefusesAnOtaTicket() {
        TicketQrReissueService service = new TicketQrReissueService(tickets, entitlements,
                mock(TicketQrGenerationRepository.class), mock(OperationsAuditEventRepository.class), idempotency,
                new ObjectMapper());

        TicketQrReissueOutcome outcome = service.reissue(
                new ReissueTicketQrCommand(ticketRef, QrRevokeReason.RECIPIENT_CHANGED, "op-2", "corr-2"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.OTA_TICKET_NOT_SUPPORTED);
        verifyNoInteractions(entitlements);
    }

    @Test
    void replaceSessionsRefusesAnOtaTicketAndNamesItsLine() {
        UUID target = UUID.randomUUID();
        var sessions = mock(WorkshopSessionRepository.class);
        var targetSession = mock(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession.class);
        when(targetSession.getReference()).thenReturn(target.toString());
        when(sessions.findByReferenceInWithLock(any())).thenReturn(List.of(targetSession));
        when(entitlements.findByTicketId(5L)).thenReturn(List.of());
        TicketSessionReplacementService service = new TicketSessionReplacementService(tickets, entitlements,
                mock(TicketSessionReplacementRepository.class), mock(CapacityHoldRepository.class), sessions,
                mock(OperationsAuditEventRepository.class), idempotency, mock(WorkshopOtaSnapshotService.class),
                mock(WorkshopPackageReservationPolicy.class));

        TicketSessionReplacementOutcome outcome = service.replace(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(ticketRef, List.of(target))),
                TicketReplacementType.CUSTOMER_ADVANCE, "op-3", "corr-3"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.OTA_TICKET_NOT_SUPPORTED);
        assertThat(outcome.failingLineIndexes()).containsExactly(0);
        verify(entitlements, never()).save(any());
    }
}
