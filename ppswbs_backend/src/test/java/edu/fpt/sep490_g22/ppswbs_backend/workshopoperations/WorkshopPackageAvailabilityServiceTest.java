package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageLocationAvailabilityRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageAvailabilityDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageLocationAvailability;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T157 (003.2 Phase 18, FR-037/FR-038): sellability, matrix synthesis and the toggle command, without a database. */
class WorkshopPackageAvailabilityServiceTest {

    private static final long PACKAGE_ID = 5L;
    private static final long LOCATION_ID = 7L;
    private static final long ACTOR_ID = 900L;

    private final WorkshopPackageLocationAvailabilityRepository availability =
            mock(WorkshopPackageLocationAvailabilityRepository.class);
    private final WorkshopPackageRepository packages = mock(WorkshopPackageRepository.class);
    private final WorkshopLocationRepository locations = mock(WorkshopLocationRepository.class);
    private final OperationsAuditEventRepository audit = mock(OperationsAuditEventRepository.class);
    private final WorkshopPackageAvailabilityService service = new WorkshopPackageAvailabilityService(
            availability, packages, locations, audit, new ObjectMapper());

    @BeforeEach
    void setUp() {
        when(availability.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private void packageIs(WorkshopPackageState state) {
        when(packages.findById(PACKAGE_ID)).thenReturn(Optional.of(
                WorkshopPackage.builder().id(PACKAGE_ID).name("Wax Casting Workshop").state(state).build()));
    }

    private void locationIs(boolean active) {
        WorkshopLocation location = WorkshopLocation.builder().id(LOCATION_ID).displayName("Hoan Kiem").active(active).build();
        when(locations.findByIdAndActiveTrue(LOCATION_ID)).thenReturn(active ? Optional.of(location) : Optional.empty());
        when(locations.existsById(LOCATION_ID)).thenReturn(true);
    }

    private void pairRow(Boolean active, long version) {
        when(availability.findByPackageIdAndLocationId(PACKAGE_ID, LOCATION_ID)).thenReturn(active == null
                ? Optional.empty()
                : Optional.of(WorkshopPackageLocationAvailability.builder().id(1L).packageId(PACKAGE_ID)
                        .locationId(LOCATION_ID).active(active).version(version).build()));
    }

    @Test
    @DisplayName("isSellable is true only for PUBLISHED x pair row active x location active")
    void isSellableCombinations() {
        for (WorkshopPackageState state : WorkshopPackageState.values()) {
            for (Boolean pair : new Boolean[] {null, Boolean.TRUE, Boolean.FALSE}) {
                for (boolean locationActive : new boolean[] {true, false}) {
                    packageIs(state);
                    pairRow(pair, 0);
                    locationIs(locationActive);
                    boolean expected = state == WorkshopPackageState.PUBLISHED && Boolean.TRUE.equals(pair)
                            && locationActive;
                    assertThat(service.isSellable(PACKAGE_ID, LOCATION_ID))
                            .as("state=%s pair=%s locationActive=%s", state, pair, locationActive)
                            .isEqualTo(expected);
                }
            }
        }
    }

    @Test
    @DisplayName("an unknown Package is not sellable")
    void unknownPackageIsNotSellable() {
        when(packages.findById(PACKAGE_ID)).thenReturn(Optional.empty());

        assertThat(service.isSellable(PACKAGE_ID, LOCATION_ID)).isFalse();
        assertThat(service.activeLocationIds(PACKAGE_ID)).isEmpty();
    }

    @Test
    @DisplayName("activeLocationIds lists only active pairs at active locations, ascending; none for a non-PUBLISHED Package")
    void activeLocationIdsResult() {
        packageIs(WorkshopPackageState.PUBLISHED);
        when(availability.findByPackageId(PACKAGE_ID)).thenReturn(List.of(
                row(9L, true), row(7L, true), row(8L, false)));
        when(locations.findByIdInAndActiveTrue(List.of(9L, 7L))).thenReturn(List.of(
                WorkshopLocation.builder().id(9L).active(true).build(),
                WorkshopLocation.builder().id(7L).active(true).build()));

        assertThat(service.activeLocationIds(PACKAGE_ID)).containsExactly(7L, 9L);

        packageIs(WorkshopPackageState.RETIRED);
        assertThat(service.activeLocationIds(PACKAGE_ID)).isEmpty();
    }

    @Test
    @DisplayName("readMatrix returns every Package x location; a missing pair is INACTIVE with version 0")
    void readMatrixSynthesizesMissingCells() {
        when(packages.findAllByOrderByIdAsc()).thenReturn(List.of(
                WorkshopPackage.builder().id(5L).name("A").state(WorkshopPackageState.PUBLISHED).build(),
                WorkshopPackage.builder().id(6L).name("B").state(WorkshopPackageState.DRAFT).build()));
        when(locations.findAll()).thenReturn(List.of(
                WorkshopLocation.builder().id(2L).displayName("Tay Ho").active(true).build(),
                WorkshopLocation.builder().id(1L).displayName("Hoan Kiem").active(true).build()));
        when(availability.findAll()).thenReturn(List.of(row(5L, 1L, true, 3L)));

        PackageAvailabilityDtos.Matrix matrix = service.readMatrix(ACTOR_ID);

        assertThat(matrix.cells()).hasSize(4);
        assertThat(matrix.locations()).extracting(PackageAvailabilityDtos.LocationHeader::id).containsExactly(1L, 2L);
        assertThat(matrix.cells()).contains(
                new PackageAvailabilityDtos.Cell(5L, 1L, PackageAvailabilityDtos.State.ACTIVE, 3L),
                new PackageAvailabilityDtos.Cell(5L, 2L, PackageAvailabilityDtos.State.INACTIVE, 0L),
                new PackageAvailabilityDtos.Cell(6L, 1L, PackageAvailabilityDtos.State.INACTIVE, 0L),
                new PackageAvailabilityDtos.Cell(6L, 2L, PackageAvailabilityDtos.State.INACTIVE, 0L));
    }

    @Test
    @DisplayName("setting the state the pair already has writes no row and no audit event")
    void sameStateIsANoOp() {
        packageIs(WorkshopPackageState.PUBLISHED);
        locationIs(true);
        pairRow(true, 4);

        PackageAvailabilityDtos.Cell cell = service.setAvailability(ACTOR_ID, PACKAGE_ID, LOCATION_ID,
                PackageAvailabilityDtos.State.ACTIVE, null, "corr-1");

        assertThat(cell.state()).isEqualTo(PackageAvailabilityDtos.State.ACTIVE);
        assertThat(cell.version()).isEqualTo(4L);
        verify(availability, never()).saveAndFlush(any());
        verify(audit, never()).save(any());
    }

    @Test
    @DisplayName("a missing pair set INACTIVE is also a no-op (a missing row already means INACTIVE)")
    void missingPairSetInactiveIsANoOp() {
        packageIs(WorkshopPackageState.PUBLISHED);
        locationIs(true);
        pairRow(null, 0);

        PackageAvailabilityDtos.Cell cell = service.setAvailability(ACTOR_ID, PACKAGE_ID, LOCATION_ID,
                PackageAvailabilityDtos.State.INACTIVE, null, null);

        assertThat(cell).isEqualTo(new PackageAvailabilityDtos.Cell(PACKAGE_ID, LOCATION_ID,
                PackageAvailabilityDtos.State.INACTIVE, 0L));
        verify(audit, never()).save(any());
    }

    @Test
    @DisplayName("a real change writes the row and exactly one audit event with {packageId, locationId, from, to}")
    void realChangeWritesRowAndOneAuditEvent() {
        packageIs(WorkshopPackageState.PUBLISHED);
        locationIs(true);
        pairRow(null, 0);

        PackageAvailabilityDtos.Cell cell = service.setAvailability(ACTOR_ID, PACKAGE_ID, LOCATION_ID,
                PackageAvailabilityDtos.State.ACTIVE, 0L, "corr-2");

        assertThat(cell.state()).isEqualTo(PackageAvailabilityDtos.State.ACTIVE);
        ArgumentCaptor<WorkshopPackageLocationAvailability> row =
                ArgumentCaptor.forClass(WorkshopPackageLocationAvailability.class);
        verify(availability).saveAndFlush(row.capture());
        assertThat(row.getValue().isActive()).isTrue();
        assertThat(row.getValue().getUpdatedByAccountReference()).isEqualTo(String.valueOf(ACTOR_ID));
        ArgumentCaptor<OperationsAuditEvent> event = ArgumentCaptor.forClass(OperationsAuditEvent.class);
        verify(audit).save(event.capture());
        assertThat(event.getValue().getAction()).isEqualTo("WORKSHOP_PACKAGE_AVAILABILITY_CHANGED");
        assertThat(event.getValue().getOutcome()).isEqualTo("SUCCESS");
        assertThat(event.getValue().getSessionId()).isNull();
        assertThat(event.getValue().getActorId()).isEqualTo(ACTOR_ID);
        assertThat(event.getValue().getCorrelationId()).isEqualTo("corr-2");
        assertThat(event.getValue().getMetadata())
                .contains("\"packageId\":5", "\"locationId\":7", "\"from\":\"INACTIVE\"", "\"to\":\"ACTIVE\"");
    }

    @Test
    @DisplayName("a stale version on a real change is OPTIMISTIC_LOCK_CONFLICT; nothing is written")
    void staleVersionConflicts() {
        packageIs(WorkshopPackageState.PUBLISHED);
        locationIs(true);
        pairRow(true, 3);

        assertThatThrownBy(() -> service.setAvailability(ACTOR_ID, PACKAGE_ID, LOCATION_ID,
                PackageAvailabilityDtos.State.INACTIVE, 2L, "corr-3"))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(availability, never()).saveAndFlush(any());
        verify(audit, never()).save(any());
    }

    @Test
    @DisplayName("an unknown Package or location is 404")
    void unknownPackageOrLocationIsNotFound() {
        when(packages.findById(PACKAGE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setAvailability(ACTOR_ID, PACKAGE_ID, LOCATION_ID,
                PackageAvailabilityDtos.State.ACTIVE, null, null)).isInstanceOf(WorkshopNotFoundException.class);

        packageIs(WorkshopPackageState.PUBLISHED);
        when(locations.existsById(anyLong())).thenReturn(false);
        assertThatThrownBy(() -> service.setAvailability(ACTOR_ID, PACKAGE_ID, LOCATION_ID,
                PackageAvailabilityDtos.State.ACTIVE, null, null)).isInstanceOf(WorkshopNotFoundException.class);
    }

    private static WorkshopPackageLocationAvailability row(long locationId, boolean active) {
        return row(PACKAGE_ID, locationId, active, 0L);
    }

    private static WorkshopPackageLocationAvailability row(long packageId, long locationId, boolean active,
                                                           long version) {
        return WorkshopPackageLocationAvailability.builder().id(locationId).packageId(packageId)
                .locationId(locationId).active(active).version(version).build();
    }
}
