package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkshopOperationsSnapshotTest {

    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final WorkshopPackageRepository packages = mock(WorkshopPackageRepository.class);
    private final CapacityHoldRepository holds = mock(CapacityHoldRepository.class);
    private final TicketSessionEntitlementRepository entitlements = mock(TicketSessionEntitlementRepository.class);
    private final WorkshopPackageReservationPolicy packagePolicy = mock(WorkshopPackageReservationPolicy.class);

    private final WorkshopOperationsFacadeImpl facade = new WorkshopOperationsFacadeImpl(
            sessions,
            holds,
            mock(AttendanceTicketRepository.class),
            entitlements,
            mock(CancellationHandoffRepository.class),
            mock(OperationsAuditEventRepository.class),
            mock(FacadeCommandIdempotencyManager.class),
            new ObjectMapper(),
            mock(CapacityHoldService.class),
            mock(AttendanceTicketService.class),
            mock(WorkshopOtaSnapshotService.class),
            packagePolicy);

    @Test
    void saleSnapshotReturnsUuidAndWorkshopFactsWithoutNumericSessionId() {
        UUID reference = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-10T02:00:00Z");
        LocalDate workshopDate = LocalDate.of(2026, 11, 10);
        WorkshopSession session = WorkshopSession.builder()
                .id(987L)
                .reference(reference.toString())
                .location(WorkshopLocation.builder().id(25L).build())
                .workshopDate(workshopDate)
                .scheduledStartAt(start)
                .scheduledEndAt(start.plusSeconds(3600))
                .capacity(10)
                .state(SessionState.PUBLISHED)
                .build();
        when(sessions.findByReference(reference.toString())).thenReturn(Optional.of(session));
        when(holds.sumActiveHoldsForSession(eq(987L), any(Instant.class))).thenReturn(2);
        when(entitlements.countActiveConfirmedSeatsForSession(987L)).thenReturn(1);

        SaleEligibilitySnapshot result = facade.getSaleEligibility(new SaleEligibilityQuery(reference));

        assertThat(result.sessionReference()).isEqualTo(reference);
        assertThat(result.customerSelectable()).isTrue();
        assertThat(result.locationReference()).isEqualTo(25L);
        assertThat(result.workshopDate()).isEqualTo(workshopDate);
        assertThat(result.remainingCapacity()).isEqualTo(7);
        assertThat(Arrays.stream(SaleEligibilitySnapshot.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("sessionReference", "customerSelectable", "saleState", "locationReference",
                        "scheduledStart", "scheduledEnd", "remainingCapacity", "workshopDate");
        verify(sessions).findByReference(reference.toString());
    }

    @Test
    void unknownSessionReferenceReturnsNoSnapshotFacts() {
        UUID reference = UUID.randomUUID();
        when(sessions.findByReference(reference.toString())).thenReturn(Optional.empty());

        assertThat(facade.getSaleEligibility(new SaleEligibilityQuery(reference))).isNull();
        verifyNoInteractions(holds, entitlements);
    }

    @Test
    void publishedPackageSnapshotIncludesItsAuthoritativeName() {
        WorkshopPackage workshopPackage = WorkshopPackage.builder()
                .id(42L)
                .name("Pottery Basics")
                .publicPrice(450_000L)
                .minParticipants(1)
                .maxParticipants(4)
                .requiredSessionCount(1)
                .sameLocationRequired(true)
                .minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED)
                .build();
        when(packages.findById(42L)).thenReturn(Optional.of(workshopPackage));
        WorkshopPackageReservationPolicy policy = new WorkshopPackageReservationPolicy(
                packages, sessions, mock(WorkshopSlotRepository.class));

        assertThat(policy.readPublishedSnapshot(42L))
                .extracting(PublishedPackageSelectionSnapshot::packageName)
                .isEqualTo("Pottery Basics");
        assertThat(Arrays.stream(PublishedPackageSelectionSnapshot.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("packageReference", "publicPerParticipantPrice", "minParticipants",
                        "maxParticipants", "requiredSessionCount", "sameLocationRequired",
                        "minimumSameDaySlotGap", "packageName");
    }
}
