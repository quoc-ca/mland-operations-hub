package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopBookingPackagePolicySnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBookingPackagePolicySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CapacityHoldService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOtaSnapshotService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CapacityHoldServiceDeadlineTest {
    @Test
    void reservesEverySessionUntilOneSharedDeadlineNoLaterThanTenMinutes() {
        var sessions = mock(WorkshopSessionRepository.class);
        var holds = mock(CapacityHoldRepository.class);
        var policySnapshots = mock(WorkshopBookingPackagePolicySnapshotRepository.class);
        var entitlements = mock(TicketSessionEntitlementRepository.class);
        var audit = mock(OperationsAuditEventRepository.class);
        var idempotency = mock(FacadeCommandIdempotencyManager.class);
        var ota = mock(WorkshopOtaSnapshotService.class);
        var firstRef = UUID.randomUUID();
        var secondRef = UUID.randomUUID();
        var first = session(11L, firstRef);
        var second = session(12L, secondRef);
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(FacadeCommandIdempotencyManager.ClaimResult.acquired(1L, 1L));
        when(sessions.findByReferenceInWithLock(anyCollection())).thenReturn(List.of(first, second));
        when(holds.sumActiveHoldsForSession(anyLong(), any())).thenReturn(0);
        when(holds.sumConfirmedHoldsForSession(anyLong())).thenReturn(0);
        when(entitlements.countActiveConfirmedSeatsForSession(anyLong())).thenReturn(0);

        var service = new CapacityHoldService(sessions, holds, policySnapshots, entitlements, audit, idempotency,
                new ObjectMapper(), ota);
        var command = new ReserveBookingCapacityCommand("BK-1", "PK-1", List.of(firstRef, secondRef),
                1, "reserve-1", "corr-1");
        Instant startedAt = Instant.now();

        var frozenPolicy = WorkshopBookingPackagePolicySnapshot.builder()
                .bookingReference("BK-1").packageReference("PK-1").requiredSessionCount(2)
                .minParticipants(1).maxParticipants(5).sameLocationRequired(true).minimumSameDaySlotGap(1).build();
        var outcome = service.reserveBookingCapacity(command, () -> null, () -> frozenPolicy);

        Instant finishedAt = Instant.now();
        assertThat(outcome.expiresAt()).isBetween(startedAt.plus(Duration.ofMinutes(10)),
                finishedAt.plus(Duration.ofMinutes(10)));
        assertThat(outcome.holdReferences()).hasSize(2);
        var saved = org.mockito.ArgumentCaptor.forClass(CapacityHold.class);
        verify(holds, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CapacityHold::getExpiresAt)
                .containsOnly(outcome.expiresAt());
        verify(policySnapshots).save(frozenPolicy);
        verify(idempotency).completeClaim(eq(1L), eq(1L), any(), isNull(), anyString());
    }

    @Test
    void capacityRejectionDoesNotPersistBookingPolicySnapshot() {
        var sessions = mock(WorkshopSessionRepository.class);
        var holds = mock(CapacityHoldRepository.class);
        var policySnapshots = mock(WorkshopBookingPackagePolicySnapshotRepository.class);
        var entitlements = mock(TicketSessionEntitlementRepository.class);
        var idempotency = mock(FacadeCommandIdempotencyManager.class);
        var ota = mock(WorkshopOtaSnapshotService.class);
        var ref = UUID.randomUUID();
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(FacadeCommandIdempotencyManager.ClaimResult.acquired(2L, 1L));
        when(sessions.findByReferenceInWithLock(anyCollection())).thenReturn(List.of(session(11L, ref)));
        when(holds.sumActiveHoldsForSession(anyLong(), any())).thenReturn(0);
        when(holds.sumConfirmedHoldsForSession(anyLong())).thenReturn(0);
        when(entitlements.countActiveConfirmedSeatsForSession(anyLong())).thenReturn(0);
        var service = new CapacityHoldService(sessions, holds, policySnapshots, entitlements,
                mock(OperationsAuditEventRepository.class), idempotency, new ObjectMapper(), ota);

        var outcome = service.reserveBookingCapacity(
                new ReserveBookingCapacityCommand("BK-2", "PK-1", List.of(ref), 11, "reserve-2", "corr-2"),
                () -> null,
                () -> WorkshopBookingPackagePolicySnapshot.builder().bookingReference("BK-2")
                        .packageReference("PK-1").requiredSessionCount(1).minParticipants(1).maxParticipants(20)
                        .sameLocationRequired(true).minimumSameDaySlotGap(0).build());

        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        verify(policySnapshots, never()).save(any());
    }

    private static WorkshopSession session(Long id, UUID reference) {
        return WorkshopSession.builder().id(id).reference(reference.toString()).capacity(10)
                .state(SessionState.PUBLISHED).build();
    }
}
