package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.FacadeCommandIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketSessionReplacementService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOtaSnapshotService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageReservationPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.2 T110 / FR-010kb: a replayed refused replacement keeps the failing line indexes. */
class TicketSessionReplacementReplayTest {

    private final FacadeCommandIdempotencyManager idempotency = mock(FacadeCommandIdempotencyManager.class);
    private final TicketSessionReplacementService service = new TicketSessionReplacementService(
            mock(AttendanceTicketRepository.class), mock(TicketSessionEntitlementRepository.class),
            mock(TicketSessionReplacementRepository.class), mock(CapacityHoldRepository.class),
            mock(WorkshopSessionRepository.class), mock(OperationsAuditEventRepository.class), idempotency,
            mock(WorkshopOtaSnapshotService.class), mock(WorkshopPackageReservationPolicy.class));

    private static ReplaceTicketSessionsCommand command() {
        UUID ticket = UUID.randomUUID();
        // The second line repeats a ticket, so lines 1 is refused as INVALID_REPLACEMENT before any lookup.
        return new ReplaceTicketSessionsCommand(List.of(
                new ReplaceTicketSessionsCommand.Line(ticket, List.of(UUID.randomUUID())),
                new ReplaceTicketSessionsCommand.Line(ticket, List.of(UUID.randomUUID()))),
                TicketReplacementType.CUSTOMER_ADVANCE, "op-replay", "corr-replay");
    }

    @Test
    void theRefusedLinesAreStoredWithTheClaimAndRestoredOnReplay() {
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ClaimResult.acquired(9L, 1L));

        TicketSessionReplacementOutcome first = service.replace(command());

        assertThat(first.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(first.failingLineIndexes()).containsExactly(1);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(idempotency).completeClaim(eq(9L), eq(1L), eq(OutcomeStatus.REJECTED),
                eq(RejectionReason.INVALID_REPLACEMENT), payload.capture());
        assertThat(payload.getValue()).isEqualTo("[1]");

        FacadeCommandIdempotencyRecord stored = FacadeCommandIdempotencyRecord.builder().id(9L)
                .outcomeStatus(OutcomeStatus.REJECTED).rejectionReason("INVALID_REPLACEMENT")
                .responsePayload(payload.getValue()).build();
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ClaimResult.alreadyCompleted(stored));

        TicketSessionReplacementOutcome replay = service.replace(command());

        assertThat(replay.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(replay.reason()).isEqualTo(RejectionReason.INVALID_REPLACEMENT);
        assertThat(replay.failingLineIndexes()).containsExactly(1);
    }

    @Test
    void aReplayWithoutStoredIndexesStillAnswersTheReason() {
        FacadeCommandIdempotencyRecord stored = FacadeCommandIdempotencyRecord.builder().id(9L)
                .outcomeStatus(OutcomeStatus.REJECTED).rejectionReason("INSUFFICIENT_CAPACITY").build();
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ClaimResult.alreadyCompleted(stored));

        TicketSessionReplacementOutcome replay = service.replace(command());

        assertThat(replay.reason()).isEqualTo(RejectionReason.INSUFFICIENT_CAPACITY);
        assertThat(replay.failingLineIndexes()).isEmpty();
    }
}
