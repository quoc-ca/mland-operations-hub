package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBookingPackagePolicySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
            packagePolicy,
            mock(WorkshopAddOnService.class));

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
    void cancellationEligibilityRequiresExactSessionSetAndReturnsOnlySafeFacts() {
        UUID sessionRef = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-10T02:00:00Z");
        WorkshopSession session = WorkshopSession.builder().id(201L).reference(sessionRef.toString())
                .scheduledStartAt(start).build();
        AttendanceTicket ticket = AttendanceTicket.builder().bookingReference("BK-CANCEL")
                .validityState(TicketValidityState.ACTIVE).build();
        TicketSessionEntitlement entitlement = TicketSessionEntitlement.builder().ticket(ticket).session(session)
                .attendanceState(AttendanceState.NOT_CHECKED_IN).build();
        when(entitlements.findActiveByBookingReference("BK-CANCEL")).thenReturn(List.of(entitlement));
        when(sessions.findByReference(sessionRef.toString())).thenReturn(Optional.of(session));

        var result = facade.getBookingCancellationEligibility(
                new BookingCancellationEligibilityQuery("BK-CANCEL"));

        assertThat(result.eligible()).isTrue();
        assertThat(result.earliestRequiredSessionStart()).isEqualTo(start);
        assertThat(result.hasCheckedInTicket()).isFalse();
        assertThat(result.hasNoShow()).isFalse();
        assertThat(Arrays.stream(BookingCancellationEligibilitySnapshot.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("eligible", "reason", "earliestRequiredSessionStart", "hasCheckedInTicket", "hasNoShow");
    }

    @Test
    void cancellationEligibilityUsesOperationsCurrentSessionSetWithoutCallerSuppliedReferences() {
        UUID actual = UUID.randomUUID();
        WorkshopSession session = WorkshopSession.builder().id(202L).reference(actual.toString()).build();
        AttendanceTicket ticket = AttendanceTicket.builder().bookingReference("BK-CANCEL-MISMATCH")
                .validityState(TicketValidityState.ACTIVE).build();
        TicketSessionEntitlement entitlement = TicketSessionEntitlement.builder().ticket(ticket).session(session)
                .attendanceState(AttendanceState.CHECKED_IN).build();
        when(entitlements.findActiveByBookingReference("BK-CANCEL-MISMATCH")).thenReturn(List.of(entitlement));

        when(sessions.findByReference(actual.toString())).thenReturn(Optional.of(session));

        var result = facade.getBookingCancellationEligibility(new BookingCancellationEligibilityQuery(
                "BK-CANCEL-MISMATCH"));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo("TICKET_CHECKED_IN");
        assertThat(result.hasCheckedInTicket()).isTrue();
        verify(sessions).findByReference(actual.toString());
    }

    @Test
    void cancellationEligibilityRejectsCheckInAndNoShowFacts() {
        UUID firstRef = UUID.randomUUID();
        UUID secondRef = UUID.randomUUID();
        WorkshopSession first = WorkshopSession.builder().id(211L).reference(firstRef.toString())
                .scheduledStartAt(Instant.parse("2026-11-10T02:00:00Z")).build();
        WorkshopSession second = WorkshopSession.builder().id(212L).reference(secondRef.toString())
                .scheduledStartAt(Instant.parse("2026-11-11T02:00:00Z")).build();
        AttendanceTicket checkedInTicket = AttendanceTicket.builder().bookingReference("BK-CANCEL-USED")
                .validityState(TicketValidityState.ACTIVE).build();
        AttendanceTicket noShowTicket = AttendanceTicket.builder().bookingReference("BK-CANCEL-USED")
                .validityState(TicketValidityState.ACTIVE).build();
        TicketSessionEntitlement checkedIn = TicketSessionEntitlement.builder().ticket(checkedInTicket).session(first)
                .attendanceState(AttendanceState.CHECKED_IN).checkedInAt(Instant.parse("2026-11-10T02:05:00Z")).build();
        TicketSessionEntitlement noShow = TicketSessionEntitlement.builder().ticket(noShowTicket).session(second)
                .attendanceState(AttendanceState.NO_SHOW).build();
        when(entitlements.findActiveByBookingReference("BK-CANCEL-USED")).thenReturn(List.of(checkedIn, noShow));
        when(sessions.findByReference(firstRef.toString())).thenReturn(Optional.of(first));
        when(sessions.findByReference(secondRef.toString())).thenReturn(Optional.of(second));

        var result = facade.getBookingCancellationEligibility(
                new BookingCancellationEligibilityQuery("BK-CANCEL-USED"));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo("TICKET_CHECKED_IN");
        assertThat(result.hasCheckedInTicket()).isTrue();
        assertThat(result.hasNoShow()).isTrue();
        assertThat(result.earliestRequiredSessionStart()).isEqualTo(first.getScheduledStartAt());
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
                packages, sessions, mock(WorkshopSlotRepository.class),
                mock(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopBookingPackagePolicySnapshotRepository.class));

        assertThat(policy.readPublishedSnapshot(42L))
                .extracting(PublishedPackageSelectionSnapshot::packageName)
                .isEqualTo("Pottery Basics");
        assertThat(Arrays.stream(PublishedPackageSelectionSnapshot.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("packageReference", "publicPerParticipantPrice", "minParticipants",
                        "maxParticipants", "requiredSessionCount", "sameLocationRequired",
                        "minimumSameDaySlotGap", "packageName");
    }

    @Test
    void bookingPolicySnapshotEnforcesChronologyLocationAndSameDaySlotGap() {
        var snapshots = mock(WorkshopBookingPackagePolicySnapshotRepository.class);
        var slots = mock(WorkshopSlotRepository.class);
        WorkshopPackageReservationPolicy policy = new WorkshopPackageReservationPolicy(packages, sessions, slots, snapshots);
        WorkshopBookingPackagePolicySnapshot silverclay = WorkshopBookingPackagePolicySnapshot.builder()
                .bookingReference("BK-SILVERCLAY")
                .packageReference("42")
                .requiredSessionCount(2)
                .minParticipants(1)
                .maxParticipants(4)
                .sameLocationRequired(true)
                .minimumSameDaySlotGap(1)
                .build();
        when(snapshots.findByBookingReference("BK-SILVERCLAY")).thenReturn(Optional.of(silverclay));
        when(packages.findById(42L)).thenReturn(Optional.of(WorkshopPackage.builder().id(42L)
                .requiredSessionCount(2).sameLocationRequired(false).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()));
        WorkshopLocation location = WorkshopLocation.builder().id(25L).build();
        when(slots.findByLocationId(25L)).thenReturn(List.of(
                slot(1L, LocalTime.of(9, 0)), slot(2L, LocalTime.of(10, 0)), slot(3L, LocalTime.of(11, 0))));

        WorkshopSession first = policySession(1L, location, slot(1L, LocalTime.of(9, 0)),
                LocalDate.of(2026, 11, 10), Instant.parse("2026-11-10T02:00:00Z"));
        WorkshopSession validSecond = policySession(2L, location, slot(3L, LocalTime.of(11, 0)),
                LocalDate.of(2026, 11, 10), Instant.parse("2026-11-10T04:00:00Z"));
        WorkshopSession adjacentSecond = policySession(3L, location, slot(2L, LocalTime.of(10, 0)),
                LocalDate.of(2026, 11, 10), Instant.parse("2026-11-10T03:00:00Z"));
        WorkshopSession otherLocation = policySession(4L, WorkshopLocation.builder().id(26L).build(),
                slot(4L, LocalTime.of(12, 0)), LocalDate.of(2026, 11, 10), Instant.parse("2026-11-10T05:00:00Z"));

        assertThat(policy.isValidReplacement("BK-SILVERCLAY", List.of(first, validSecond), 2)).isTrue();
        assertThat(policy.isValidReplacement("BK-SILVERCLAY", List.of(first, adjacentSecond), 2)).isFalse();
        assertThat(policy.isValidReplacement("BK-SILVERCLAY", List.of(validSecond, first), 2)).isFalse();
        assertThat(policy.isValidReplacement("BK-SILVERCLAY", List.of(first, otherLocation), 2)).isFalse();
        assertThat(policy.isValidReplacement("BK-SILVERCLAY", List.of(first), 2)).isFalse();
        verify(slots, atLeastOnce()).findByLocationId(25L);
        verifyNoInteractions(packages);
    }

    @Test
    void replacementWithoutBookingTimeSnapshotFailsClosed() {
        var snapshots = mock(WorkshopBookingPackagePolicySnapshotRepository.class);
        WorkshopPackageReservationPolicy policy = new WorkshopPackageReservationPolicy(
                packages, sessions, mock(WorkshopSlotRepository.class), snapshots);
        when(snapshots.findByBookingReference("BK-LEGACY")).thenReturn(Optional.empty());

        assertThat(policy.isValidReplacement("BK-LEGACY", List.of(), 1)).isFalse();
    }

    private static WorkshopSlot slot(Long id, LocalTime start) {
        return WorkshopSlot.builder().id(id).startTime(start).build();
    }

    private static WorkshopSession policySession(Long id, WorkshopLocation location, WorkshopSlot slot,
                                                  LocalDate date, Instant start) {
        return WorkshopSession.builder().id(id).location(location).slot(slot).workshopDate(date)
                .scheduledStartAt(start).scheduledEndAt(start.plusSeconds(3600)).build();
    }
}
