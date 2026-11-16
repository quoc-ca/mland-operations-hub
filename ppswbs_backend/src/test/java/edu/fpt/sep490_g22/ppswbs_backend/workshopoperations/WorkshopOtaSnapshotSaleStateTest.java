package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionOtaSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSessionOtaSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOtaSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T114: the session-state to OTA {@code saleState} mapping, in particular that a paused session is not {@code CLOSED}. */
class WorkshopOtaSnapshotSaleStateTest {

    @ParameterizedTest
    @CsvSource({
            "PUBLISHED,PUBLISHED",
            "SALES_PAUSED,SALES_PAUSED",
            "IN_PROGRESS,CLOSED",
            "CANCELLATION_REQUESTED,CLOSED",
            "CANCELLED,CLOSED",
            "ATTENDANCE_FINALIZED,CLOSED"
    })
    void mapsSessionStateToSaleState(SessionState state, String expectedSaleState) {
        WorkshopSessionOtaSnapshotRepository snapshots = mock(WorkshopSessionOtaSnapshotRepository.class);
        CapacityHoldRepository holds = mock(CapacityHoldRepository.class);
        TicketSessionEntitlementRepository entitlements = mock(TicketSessionEntitlementRepository.class);
        when(snapshots.findTopBySessionReferenceOrderBySourceVersionDesc(any())).thenReturn(Optional.empty());
        when(snapshots.save(any(WorkshopSessionOtaSnapshot.class))).thenAnswer(i -> i.getArgument(0));
        when(holds.sumActiveHoldsForSession(anyLong(), any())).thenReturn(0);
        when(entitlements.countActiveConfirmedSeatsForSession(anyLong())).thenReturn(0);
        WorkshopOtaSnapshotService service = new WorkshopOtaSnapshotService(
                snapshots, holds, entitlements, Mockito.mock(ApplicationEventPublisher.class));
        WorkshopLocation location = WorkshopLocation.builder().code("L").displayName("L").addressSnapshot("a").build();
        WorkshopSession session = WorkshopSession.builder().id(1L).reference("S-1").location(location)
                .capacity(10).state(state).build();

        var snapshot = service.publishForObservableChange(session, "change", Instant.now());

        assertThat(snapshot.saleState()).isEqualTo(expectedSaleState);
    }

    @Test
    void draftSessionPublishesNothing() {
        WorkshopOtaSnapshotService service = new WorkshopOtaSnapshotService(
                mock(WorkshopSessionOtaSnapshotRepository.class), mock(CapacityHoldRepository.class),
                mock(TicketSessionEntitlementRepository.class), mock(ApplicationEventPublisher.class));
        WorkshopSession session = WorkshopSession.builder().id(1L).reference("S-1").state(SessionState.DRAFT).build();

        assertThat(service.publishForObservableChange(session, "change", Instant.now())).isNull();
    }
}
