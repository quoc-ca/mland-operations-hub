package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityHoldOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 003.2 T085 — Comprehensive Wax Casting calendar validation tests:
 * - Exactly two distinct chronological sessions.
 * - Same location enforced.
 * - At least 2 Monday-Friday foundry working days strictly between session dates (count >= 2, no maximum;
 *   owner decision 2026-11-05, 003.2 T089).
 * - Saturday and Sunday are excluded from the working day count.
 * - Slot / time ordinals never substitute for calendar date gap.
 * - Rejections return safe typed PACKAGE_SELECTION_INVALID.
 * - No partial hold, booking, or ticket mutation when validation fails.
 */
class WaxCastingScheduleValidationTest {

    /**
     * Every case below pins a weekday pattern, not a calendar date. Anchoring them to a Monday that is
     * always ahead of today keeps the patterns intact while the sessions stay sellable, which the policy
     * requires: it refuses a session whose start is not in the future.
     */
    private static final LocalDate MONDAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);

    private WorkshopPackageRepository packageRepository;
    private WorkshopSessionRepository sessionRepository;
    private WorkshopSlotRepository slotRepository;
    private WorkshopBookingPackagePolicySnapshotRepository bookingPolicySnapshotRepository;
    private WorkshopPackageReservationPolicy policy;

    private WorkshopLocation locationA;
    private WorkshopLocation locationB;
    private WorkshopSlot slotMorning;
    private WorkshopSlot slotAfternoon;
    private WorkshopSlot slotEvening;
    private WorkshopPackage waxCastingPackage;

    @BeforeEach
    void setUp() {
        packageRepository = mock(WorkshopPackageRepository.class);
        sessionRepository = mock(WorkshopSessionRepository.class);
        slotRepository = mock(WorkshopSlotRepository.class);
        bookingPolicySnapshotRepository = mock(WorkshopBookingPackagePolicySnapshotRepository.class);
        policy = new WorkshopPackageReservationPolicy(packageRepository, sessionRepository, slotRepository, bookingPolicySnapshotRepository);

        locationA = WorkshopLocation.builder().id(101L).code("LOC-A").displayName("Saigon Center").active(true).build();
        locationB = WorkshopLocation.builder().id(102L).code("LOC-B").displayName("Thao Dien Hub").active(true).build();

        slotMorning = WorkshopSlot.builder().id(1L).location(locationA).label("Morning").startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(12, 0)).build();
        slotAfternoon = WorkshopSlot.builder().id(2L).location(locationA).label("Afternoon").startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(16, 0)).build();
        slotEvening = WorkshopSlot.builder().id(3L).location(locationA).label("Evening").startTime(LocalTime.of(16, 0)).endTime(LocalTime.of(19, 0)).build();

        waxCastingPackage = WorkshopPackage.builder()
                .id(88L)
                .name("Wax Casting Workshop")
                .description("Two sessions with at least two foundry working days between")
                .publicPrice(1_000_000L)
                .minParticipants(1)
                .maxParticipants(6)
                .requiredSessionCount(2)
                .sameLocationRequired(true)
                .minimumSameDaySlotGap(0)
                .requiredFoundryWorkingDaysBetweenSessions(2)
                .state(WorkshopPackageState.PUBLISHED)
                .build();
    }

    // =========================================================================
    // 1. Unit tests for countFoundryWorkingDaysBetween
    // =========================================================================

    @Test
    @DisplayName("Working day count: Monday to Thursday has exactly 2 days (Tue, Wed)")
    void countWorkingDaysMondayToThursday() {
        LocalDate mon = MONDAY.plusDays(0);
        LocalDate thu = MONDAY.plusDays(3);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(mon, thu)).isEqualTo(2);
    }

    @Test
    @DisplayName("Working day count: Friday to Wednesday next week has exactly 2 days (Mon, Tue; Sat & Sun skipped)")
    void countWorkingDaysFridayToWednesdayNextWeek() {
        LocalDate fri = MONDAY.plusDays(4);
        LocalDate wed = MONDAY.plusDays(9);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(fri, wed)).isEqualTo(2);
    }

    @Test
    @DisplayName("Working day count: Monday to Wednesday has only 1 day (Tue)")
    void countWorkingDaysMondayToWednesday() {
        LocalDate mon = MONDAY.plusDays(0);
        LocalDate wed = MONDAY.plusDays(2);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(mon, wed)).isEqualTo(1);
    }

    @Test
    @DisplayName("Working day count: Friday to Tuesday next week has only 1 day (Mon; Sat & Sun skipped)")
    void countWorkingDaysFridayToTuesdayNextWeek() {
        LocalDate fri = MONDAY.plusDays(4);
        LocalDate tue = MONDAY.plusDays(8);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(fri, tue)).isEqualTo(1);
    }

    @Test
    @DisplayName("Working day count: Monday to Friday has 3 working days")
    void countWorkingDaysMondayToFriday() {
        LocalDate mon = MONDAY.plusDays(0);
        LocalDate fri = MONDAY.plusDays(4);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(mon, fri)).isEqualTo(3);
    }

    @Test
    @DisplayName("Working day count: Friday to Thursday next week has 3 working days")
    void countWorkingDaysFridayToThursdayNextWeek() {
        LocalDate fri = MONDAY.plusDays(4);
        LocalDate thu = MONDAY.plusDays(10);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(fri, thu)).isEqualTo(3);
    }

    @Test
    @DisplayName("Working day count: Saturday and Sunday are excluded from count")
    void countWorkingDaysExcludesSaturdayAndSunday() {
        // Saturday (Nov 14) to Wednesday (Nov 18): Sunday skipped; Monday & Tuesday counted = 2
        LocalDate sat = MONDAY.plusDays(5);
        LocalDate wed = MONDAY.plusDays(9);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(sat, wed)).isEqualTo(2);

        // Sunday (Nov 15) to Wednesday (Nov 18): Monday & Tuesday counted = 2
        LocalDate sun = MONDAY.plusDays(6);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(sun, wed)).isEqualTo(2);

        // Saturday (Nov 14) to Tuesday (Nov 17): Sunday skipped; Monday counted = 1
        LocalDate tue = MONDAY.plusDays(8);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(sat, tue)).isEqualTo(1);

        // Friday (Nov 13) to Sunday (Nov 15): Saturday skipped = 0
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(MONDAY.plusDays(4), sun)).isEqualTo(0);
    }

    @Test
    @DisplayName("Working day count: Same day or out-of-order dates return 0")
    void countWorkingDaysSameDayOrOutOfOrder() {
        LocalDate mon = MONDAY.plusDays(0);
        LocalDate thu = MONDAY.plusDays(3);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(mon, mon)).isEqualTo(0);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(thu, mon)).isEqualTo(0);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(null, thu)).isEqualTo(0);
        assertThat(WorkshopPackageReservationPolicy.countFoundryWorkingDaysBetween(mon, null)).isEqualTo(0);
    }

    // =========================================================================
    // 2. Unit tests for validateFoundryWorkingDayGap
    // =========================================================================

    @Test
    @DisplayName("Foundry gap validation: at least 2 working days required, no maximum")
    void validateFoundryWorkingDayGapCases() {
        WorkshopSession monSession = session(1L, UUID.randomUUID(), locationA, slotEvening, MONDAY.plusDays(0));
        WorkshopSession wedSession = session(2L, UUID.randomUUID(), locationA, slotMorning, MONDAY.plusDays(2));
        WorkshopSession thuSession = session(3L, UUID.randomUUID(), locationA, slotMorning, MONDAY.plusDays(3));
        WorkshopSession friSession = session(4L, UUID.randomUUID(), locationA, slotEvening, MONDAY.plusDays(4));
        WorkshopSession nextTueSession = session(5L, UUID.randomUUID(), locationA, slotMorning, MONDAY.plusDays(8));
        WorkshopSession nextWedSession = session(6L, UUID.randomUUID(), locationA, slotMorning, MONDAY.plusDays(9));

        // Monday -> Thursday is valid (2 days: Tue, Wed)
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(List.of(monSession, thuSession), 2)).isTrue();

        // Friday -> Wednesday next week is valid (2 days: Mon, Tue)
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(List.of(friSession, nextWedSession), 2)).isTrue();

        // Monday -> Wednesday is invalid (only 1 day: Tue)
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(List.of(monSession, wedSession), 2)).isFalse();

        // Friday -> Tuesday next week is invalid (only 1 day: Mon)
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(List.of(friSession, nextTueSession), 2)).isFalse();

        // Monday -> Friday is valid (3 days: Tue, Wed, Thu; at least 2)
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(List.of(monSession, friSession), 2)).isTrue();

        // Monday -> Wednesday next week is valid (6 days; no maximum gap)
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(List.of(monSession, nextWedSession), 2)).isTrue();

        // Reverse chronological order is invalid
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(List.of(thuSession, monSession), 2)).isFalse();

        // When requiredDays == 0, gap is not enforced (valid)
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(List.of(monSession, wedSession), 0)).isTrue();
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(List.of(monSession), 0)).isTrue();

        // When requiredDays > 0 but fewer than 2 sessions -> false
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(List.of(monSession), 2)).isFalse();
        assertThat(WorkshopPackageReservationPolicy.validateFoundryWorkingDayGap(Collections.emptyList(), 2)).isFalse();
    }

    // =========================================================================
    // 3. Tests for WorkshopPackageReservationPolicy.validate(command)
    // =========================================================================

    @Test
    @DisplayName("validate accepts valid Wax Casting sessions: Monday 16:00 and Thursday 09:30")
    void validateAcceptsMondayToThursday() {
        UUID ref1 = UUID.randomUUID();
        UUID ref2 = UUID.randomUUID();
        // Monday 16:00 (slotEvening) and Thursday 09:30 (slotMorning)
        WorkshopSession s1 = session(10L, ref1, locationA, slotEvening, MONDAY.plusDays(0));
        WorkshopSession s2 = session(11L, ref2, locationA, slotMorning, MONDAY.plusDays(3));

        when(packageRepository.findByIdWithLock(88L)).thenReturn(Optional.of(waxCastingPackage));
        when(sessionRepository.findByReferenceInWithLock(anyList())).thenReturn(List.of(s1, s2));

        ReserveBookingCapacityCommand command = new ReserveBookingCapacityCommand(
                "BK-WAX-001", "88", List.of(ref1, ref2), 2, "op-1", "corr-1");

        RejectionReason rejection = policy.validate(command);
        assertThat(rejection).isNull();
    }

    @Test
    @DisplayName("validate accepts valid Wax Casting sessions: Friday 16:00 and Wednesday 09:30 next week")
    void validateAcceptsFridayToWednesdayNextWeek() {
        UUID ref1 = UUID.randomUUID();
        UUID ref2 = UUID.randomUUID();
        WorkshopSession s1 = session(12L, ref1, locationA, slotEvening, MONDAY.plusDays(4));
        WorkshopSession s2 = session(13L, ref2, locationA, slotMorning, MONDAY.plusDays(9));

        when(packageRepository.findByIdWithLock(88L)).thenReturn(Optional.of(waxCastingPackage));
        when(sessionRepository.findByReferenceInWithLock(anyList())).thenReturn(List.of(s1, s2));

        ReserveBookingCapacityCommand command = new ReserveBookingCapacityCommand(
                "BK-WAX-002", "88", List.of(ref1, ref2), 2, "op-2", "corr-2");

        RejectionReason rejection = policy.validate(command);
        assertThat(rejection).isNull();
    }

    @Test
    @DisplayName("validate rejects invalid Wax Casting: Monday to Wednesday (only 1 working day)")
    void validateRejectsMondayToWednesday() {
        UUID ref1 = UUID.randomUUID();
        UUID ref2 = UUID.randomUUID();
        // Mon 09:30 and Wed 16:00 (even with 6 slot ordinals difference, only 1 date strictly between)
        WorkshopSession s1 = session(14L, ref1, locationA, slotMorning, MONDAY.plusDays(0));
        WorkshopSession s2 = session(15L, ref2, locationA, slotEvening, MONDAY.plusDays(2));

        when(packageRepository.findByIdWithLock(88L)).thenReturn(Optional.of(waxCastingPackage));
        when(sessionRepository.findByReferenceInWithLock(anyList())).thenReturn(List.of(s1, s2));

        ReserveBookingCapacityCommand command = new ReserveBookingCapacityCommand(
                "BK-WAX-003", "88", List.of(ref1, ref2), 2, "op-3", "corr-3");

        RejectionReason rejection = policy.validate(command);
        assertThat(rejection).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
    }

    @Test
    @DisplayName("validate rejects invalid Wax Casting: Friday to Tuesday next week (only 1 working day)")
    void validateRejectsFridayToTuesdayNextWeek() {
        UUID ref1 = UUID.randomUUID();
        UUID ref2 = UUID.randomUUID();
        WorkshopSession s1 = session(16L, ref1, locationA, slotMorning, MONDAY.plusDays(4));
        WorkshopSession s2 = session(17L, ref2, locationA, slotEvening, MONDAY.plusDays(8));

        when(packageRepository.findByIdWithLock(88L)).thenReturn(Optional.of(waxCastingPackage));
        when(sessionRepository.findByReferenceInWithLock(anyList())).thenReturn(List.of(s1, s2));

        ReserveBookingCapacityCommand command = new ReserveBookingCapacityCommand(
                "BK-WAX-004", "88", List.of(ref1, ref2), 2, "op-4", "corr-4");

        RejectionReason rejection = policy.validate(command);
        assertThat(rejection).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
    }

    @Test
    @DisplayName("validate accepts Wax Casting: Monday to Friday (3 working days, at least 2)")
    void validateAcceptsMondayToFridayThreeWorkingDays() {
        UUID ref1 = UUID.randomUUID();
        UUID ref2 = UUID.randomUUID();
        WorkshopSession s1 = session(18L, ref1, locationA, slotMorning, MONDAY.plusDays(0));
        WorkshopSession s2 = session(19L, ref2, locationA, slotEvening, MONDAY.plusDays(4));

        when(packageRepository.findByIdWithLock(88L)).thenReturn(Optional.of(waxCastingPackage));
        when(sessionRepository.findByReferenceInWithLock(anyList())).thenReturn(List.of(s1, s2));

        ReserveBookingCapacityCommand command = new ReserveBookingCapacityCommand(
                "BK-WAX-005", "88", List.of(ref1, ref2), 2, "op-5", "corr-5");

        RejectionReason rejection = policy.validate(command);
        assertThat(rejection).isNull();
    }

    @Test
    @DisplayName("validate rejects Wax Casting sessions at different locations")
    void validateRejectsDifferentLocations() {
        UUID ref1 = UUID.randomUUID();
        UUID ref2 = UUID.randomUUID();
        // Mon at Location A, Thu at Location B
        WorkshopSession s1 = session(20L, ref1, locationA, slotMorning, MONDAY.plusDays(0));
        WorkshopSession s2 = session(21L, ref2, locationB, slotMorning, MONDAY.plusDays(3));

        when(packageRepository.findByIdWithLock(88L)).thenReturn(Optional.of(waxCastingPackage));
        when(sessionRepository.findByReferenceInWithLock(anyList())).thenReturn(List.of(s1, s2));

        ReserveBookingCapacityCommand command = new ReserveBookingCapacityCommand(
                "BK-WAX-006", "88", List.of(ref1, ref2), 2, "op-6", "corr-6");

        RejectionReason rejection = policy.validate(command);
        assertThat(rejection).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
    }

    @Test
    @DisplayName("validate rejects duplicate identical sessions (session trùng nhau)")
    void validateRejectsDuplicateSessions() {
        UUID ref1 = UUID.randomUUID();
        WorkshopSession s1 = session(22L, ref1, locationA, slotMorning, MONDAY.plusDays(0));

        when(packageRepository.findByIdWithLock(88L)).thenReturn(Optional.of(waxCastingPackage));

        // Command passes same session UUID twice
        ReserveBookingCapacityCommand command = new ReserveBookingCapacityCommand(
                "BK-WAX-007", "88", List.of(ref1, ref1), 2, "op-7", "corr-7");

        RejectionReason rejection = policy.validate(command);
        assertThat(rejection).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
    }

    @Test
    @DisplayName("validate rejects out-of-order chronological sessions (Thursday before Monday)")
    void validateRejectsOutOfOrderChronologicalSessions() {
        UUID refThu = UUID.randomUUID();
        UUID refMon = UUID.randomUUID();
        WorkshopSession sThu = session(23L, refThu, locationA, slotMorning, MONDAY.plusDays(3));
        WorkshopSession sMon = session(24L, refMon, locationA, slotMorning, MONDAY.plusDays(0));

        when(packageRepository.findByIdWithLock(88L)).thenReturn(Optional.of(waxCastingPackage));
        when(sessionRepository.findByReferenceInWithLock(anyList())).thenReturn(List.of(sThu, sMon));

        // Passed in reverse order: Thursday first, Monday second
        ReserveBookingCapacityCommand command = new ReserveBookingCapacityCommand(
                "BK-WAX-008", "88", List.of(refThu, refMon), 2, "op-8", "corr-8");

        RejectionReason rejection = policy.validate(command);
        assertThat(rejection).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
    }

    // =========================================================================
    // 4. Tests for WorkshopPackageReservationPolicy.isValidReplacement
    // =========================================================================

    @Test
    @DisplayName("isValidReplacement enforces 2 working days, same location, distinct, and chronological order")
    void isValidReplacementEnforcesWaxCastingPolicy() {
        WorkshopBookingPackagePolicySnapshot waxSnapshot = WorkshopBookingPackagePolicySnapshot.builder()
                .bookingReference("BK-REPLACE-WAX")
                .packageReference("88")
                .requiredSessionCount(2)
                .minParticipants(1)
                .maxParticipants(6)
                .sameLocationRequired(true)
                .minimumSameDaySlotGap(0)
                .requiredFoundryWorkingDaysBetweenSessions(2)
                .build();
        when(bookingPolicySnapshotRepository.findByBookingReference("BK-REPLACE-WAX")).thenReturn(Optional.of(waxSnapshot));

        WorkshopSession sMon = session(31L, UUID.randomUUID(), locationA, slotEvening, MONDAY.plusDays(0));
        WorkshopSession sThu = session(32L, UUID.randomUUID(), locationA, slotMorning, MONDAY.plusDays(3));
        WorkshopSession sWed = session(33L, UUID.randomUUID(), locationA, slotMorning, MONDAY.plusDays(2));
        WorkshopSession sThuLocB = session(34L, UUID.randomUUID(), locationB, slotMorning, MONDAY.plusDays(3));

        // Valid: Monday -> Thursday (2 days)
        assertThat(policy.isValidReplacement("BK-REPLACE-WAX", List.of(sMon, sThu), 2)).isTrue();

        // Invalid: Monday -> Wednesday (1 day)
        assertThat(policy.isValidReplacement("BK-REPLACE-WAX", List.of(sMon, sWed), 2)).isFalse();

        // Invalid: Reverse order (Thursday -> Monday)
        assertThat(policy.isValidReplacement("BK-REPLACE-WAX", List.of(sThu, sMon), 2)).isFalse();

        // Invalid: Different locations
        assertThat(policy.isValidReplacement("BK-REPLACE-WAX", List.of(sMon, sThuLocB), 2)).isFalse();

        // Invalid: Duplicate sessions
        assertThat(policy.isValidReplacement("BK-REPLACE-WAX", List.of(sMon, sMon), 2)).isFalse();

        // Invalid: Only 1 session provided
        assertThat(policy.isValidReplacement("BK-REPLACE-WAX", List.of(sMon), 2)).isFalse();
    }

    // =========================================================================
    // 5. Test no partial hold / booking mutation when validation fails
    // =========================================================================

    @Test
    @DisplayName("reserveBookingCapacity creates no hold or snapshot mutation on validation failure")
    void reserveBookingCapacityNoMutationOnFailure() {
        var sessions = mock(WorkshopSessionRepository.class);
        var holds = mock(CapacityHoldRepository.class);
        var policySnapshots = mock(WorkshopBookingPackagePolicySnapshotRepository.class);
        var entitlements = mock(TicketSessionEntitlementRepository.class);
        var audit = mock(OperationsAuditEventRepository.class);
        var idempotency = mock(FacadeCommandIdempotencyManager.class);
        var ota = mock(WorkshopOtaSnapshotService.class);

        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(FacadeCommandIdempotencyManager.ClaimResult.acquired(1L, 1L));

        CapacityHoldService holdService = new CapacityHoldService(
                sessions, holds, policySnapshots, entitlements, audit, idempotency, new ObjectMapper(), ota);

        ReserveBookingCapacityCommand command = new ReserveBookingCapacityCommand(
                "BK-FAIL", "88", List.of(UUID.randomUUID(), UUID.randomUUID()), 2, "op-fail", "corr-fail");

        // Supplier returns PACKAGE_SELECTION_INVALID
        CapacityHoldOutcome outcome = holdService.reserveBookingCapacity(
                command,
                () -> RejectionReason.PACKAGE_SELECTION_INVALID,
                () -> null);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);

        // Verify zero persistence mutations: no holds saved, no snapshots saved, no entitlement mutations
        verify(holds, never()).save(any());
        verify(policySnapshots, never()).save(any());
        verifyNoInteractions(entitlements);
    }

    // =========================================================================
    // Helper method
    // =========================================================================

    private static WorkshopSession session(Long id, UUID reference, WorkshopLocation location,
                                           WorkshopSlot slot, LocalDate date) {
        Instant start = date.atTime(slot.getStartTime()).toInstant(ZoneOffset.UTC);
        Instant end = date.atTime(slot.getEndTime()).toInstant(ZoneOffset.UTC);
        return WorkshopSession.builder()
                .id(id)
                .reference(reference.toString())
                .location(location)
                .slot(slot)
                .workshopDate(date)
                .scheduledStartAt(start)
                .scheduledEndAt(end)
                .state(SessionState.PUBLISHED)
                .capacity(10)
                .build();
    }
}

