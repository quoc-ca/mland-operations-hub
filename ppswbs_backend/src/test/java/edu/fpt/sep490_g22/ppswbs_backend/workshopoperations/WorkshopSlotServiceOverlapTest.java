package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLocationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSlotConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FR-001j: only ACTIVE slots of a location hold their time; inactive slots never block. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkshopSlotServiceOverlapTest {

    private static final String AUTH = "Bearer t";
    private static final Long LOCATION_ID = 1L;

    @Mock private WorkshopSlotRepository slotRepository;
    @Mock private WorkshopLocationService locationService;
    @Mock private WorkshopOperationsAuthorizationService authorizationService;
    @Mock private OperationsAuditEventRepository auditEventRepository;
    @InjectMocks private WorkshopSlotService service;

    private WorkshopLocation location;

    @BeforeEach
    void setUp() {
        location = WorkshopLocation.builder().id(LOCATION_ID).code("LOC1").build();
        when(locationService.requireLocationEntityInScope(any(), any())).thenReturn(location);
        AccountSummary manager = mock(AccountSummary.class);
        when(manager.accountId()).thenReturn(7L);
        when(authorizationService.authenticate(any())).thenReturn(manager);
        when(slotRepository.save(any(WorkshopSlot.class))).thenAnswer(i -> i.getArgument(0));
    }

    private WorkshopSlot slot(long id, String label, int fromH, int fromM, int toH, int toM, boolean active) {
        return WorkshopSlot.builder().id(id).reference("ref-" + id).location(location).label(label)
                .startTime(LocalTime.of(fromH, fromM)).endTime(LocalTime.of(toH, toM)).active(active).build();
    }

    private void givenSlots(WorkshopSlot... slots) {
        List<WorkshopSlot> all = List.of(slots);
        when(slotRepository.findByLocationId(LOCATION_ID)).thenReturn(all);
        when(slotRepository.findByLocationIdAndActiveTrue(LOCATION_ID))
                .thenReturn(all.stream().filter(WorkshopSlot::isActive).toList());
        for (WorkshopSlot s : all) {
            when(slotRepository.findById(s.getId())).thenReturn(Optional.of(s));
        }
    }

    private static SlotRequest patch(Integer fromH, Integer fromM, Integer toH, Integer toM, Boolean active) {
        return SlotRequest.builder()
                .startsAtLocal(fromH == null ? null : LocalTime.of(fromH, fromM))
                .endsAtLocal(toH == null ? null : LocalTime.of(toH, toM))
                .active(active).build();
    }

    @Test
    @DisplayName("Update to a time that overlaps only an INACTIVE slot succeeds")
    void updateOverlappingInactiveSlotSucceeds() {
        WorkshopSlot morning = slot(1, "Morning", 9, 0, 12, 0, true);
        WorkshopSlot afternoonOff = slot(2, "Afternoon", 13, 0, 16, 0, false);
        givenSlots(morning, afternoonOff);

        SlotResponse response = service.updateSlot(AUTH, "1", patch(14, 15, 16, 15, null));

        assertThat(morning.getStartTime()).isEqualTo(LocalTime.of(14, 15));
        assertThat(morning.getEndTime()).isEqualTo(LocalTime.of(16, 15));
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Update to a time that overlaps an ACTIVE slot is rejected with the overlap conflict")
    void updateOverlappingActiveSlotRejected() {
        WorkshopSlot morning = slot(1, "Morning", 9, 0, 12, 0, true);
        WorkshopSlot afternoon = slot(2, "Afternoon", 13, 0, 16, 0, true);
        givenSlots(morning, afternoon);

        assertThatThrownBy(() -> service.updateSlot(AUTH, "1", patch(14, 15, 16, 15, null)))
                .isInstanceOf(WorkshopSlotConflictException.class);
        assertThat(morning.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        verify(slotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Activating an inactive slot that overlaps an ACTIVE slot is rejected, even when only active changes")
    void activateOverlappingActiveSlotRejected() {
        WorkshopSlot morning = slot(1, "Morning", 9, 0, 12, 0, true);
        WorkshopSlot clash = slot(2, "Clash", 11, 0, 14, 0, false);
        givenSlots(morning, clash);

        assertThatThrownBy(() -> service.updateSlot(AUTH, "2", patch(null, null, null, null, true)))
                .isInstanceOf(WorkshopSlotConflictException.class);
        assertThat(clash.isActive()).isFalse();
        verify(slotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Activating an inactive slot with no active overlap succeeds")
    void activateWithoutOverlapSucceeds() {
        WorkshopSlot morning = slot(1, "Morning", 9, 0, 12, 0, true);
        WorkshopSlot afternoonOff = slot(2, "Afternoon", 13, 0, 16, 0, false);
        givenSlots(morning, afternoonOff);

        service.updateSlot(AUTH, "2", patch(null, null, null, null, true));

        assertThat(afternoonOff.isActive()).isTrue();
    }

    @Test
    @DisplayName("Editing an inactive slot to overlap an active slot is allowed (it holds no time); Activate then refuses")
    void inactiveSlotNeedsNoCheckUntilActivated() {
        WorkshopSlot morning = slot(1, "Morning", 9, 0, 12, 0, true);
        WorkshopSlot off = slot(2, "Off", 13, 0, 16, 0, false);
        givenSlots(morning, off);

        service.updateSlot(AUTH, "2", patch(10, 0, 13, 0, null));
        assertThat(off.getStartTime()).isEqualTo(LocalTime.of(10, 0));

        assertThatThrownBy(() -> service.updateSlot(AUTH, "2", patch(null, null, null, null, true)))
                .isInstanceOf(WorkshopSlotConflictException.class);
    }

    @Test
    @DisplayName("Create overlapping an INACTIVE slot succeeds; overlapping an ACTIVE slot is rejected")
    void createChecksOnlyActiveSlots() {
        givenSlots(slot(1, "Morning", 9, 0, 12, 0, true), slot(2, "Off", 13, 0, 16, 0, false));

        SlotRequest overInactive = SlotRequest.builder().label("New").active(true)
                .startsAtLocal(LocalTime.of(14, 0)).endsAtLocal(LocalTime.of(15, 0)).build();
        assertThat(service.createSlot(AUTH, LOCATION_ID, overInactive)).isNotNull();

        SlotRequest overActive = SlotRequest.builder().label("Bad").active(true)
                .startsAtLocal(LocalTime.of(11, 0)).endsAtLocal(LocalTime.of(13, 0)).build();
        assertThatThrownBy(() -> service.createSlot(AUTH, LOCATION_ID, overActive))
                .isInstanceOf(WorkshopSlotConflictException.class);
    }
}
