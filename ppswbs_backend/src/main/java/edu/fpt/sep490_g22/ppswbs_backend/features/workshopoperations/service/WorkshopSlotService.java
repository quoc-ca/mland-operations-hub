package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Scoped Workshop Slot management service adapting to canonical WorkshopLocation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopSlotService {

    private final WorkshopSlotRepository slotRepository;
    private final WorkshopLocationService locationService;

    @Transactional(readOnly = true)
    public List<SlotResponse> listSlots(String authHeader, Long locationId) {
        WorkshopLocation location = locationService.requireLocationEntityInScope(authHeader, locationId);
        List<WorkshopSlot> slots = slotRepository.findByLocationId(location.getId());

        return slots.stream()
                .map(s -> toSlotResponse(s, location))
                .toList();
    }

    @Transactional
    public SlotResponse createSlot(String authHeader, Long locationId, SlotRequest request) {
        WorkshopLocation location = locationService.requireLocationEntityInScope(authHeader, locationId);
        validateSlotRequest(request);

        List<WorkshopSlot> existingSlots = slotRepository.findByLocationId(location.getId());
        for (WorkshopSlot existing : existingSlots) {
            if (isOverlapping(request.getStartsAtLocal(), request.getEndsAtLocal(),
                    existing.getStartTime(), existing.getEndTime())) {
                log.warn("Slot overlap conflict: requested {}-{} overlaps with existing slot '{}' ({}-{}) in location {}",
                        request.getStartsAtLocal(), request.getEndsAtLocal(),
                        existing.getLabel(), existing.getStartTime(), existing.getEndTime(), location.getCode());
                throw new WorkshopSlotConflictException("Slot interval " + request.getStartsAtLocal() + " - " +
                        request.getEndsAtLocal() + " overlaps with configured slot '" + existing.getLabel() + "' (" +
                        existing.getStartTime() + " - " + existing.getEndTime() + ")");
            }
        }

        WorkshopSlot slot = WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(location)
                .label(request.getLabel().trim())
                .startTime(request.getStartsAtLocal())
                .endTime(request.getEndsAtLocal())
                .defaultCapacity(request.getDefaultCapacity())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        WorkshopSlot saved = slotRepository.save(slot);
        log.info("Created slot id={}, reference={}, label='{}' in location id={}",
                saved.getId(), saved.getReference(), saved.getLabel(), location.getId());

        return toSlotResponse(saved, location);
    }

    @Transactional
    public SlotResponse updateSlot(String authHeader, String slotIdentifier, SlotRequest request) {
        WorkshopSlot slot = resolveSlot(slotIdentifier);
        WorkshopLocation location = locationService.requireLocationEntityInScope(authHeader, slot.getLocation().getId());

        if (request == null) {
            throw new WorkshopValidationException("Slot patch request must not be null");
        }

        if (request.getLabel() != null && !request.getLabel().trim().isBlank()) {
            slot.setLabel(request.getLabel().trim());
        }

        if (request.getDefaultCapacity() != null) {
            if (request.getDefaultCapacity() < 1) {
                throw new WorkshopValidationException("Slot default capacity must be at least 1");
            }
            slot.setDefaultCapacity(request.getDefaultCapacity());
        }

        if (request.getActive() != null) {
            slot.setActive(request.getActive());
        }

        if (request.getStartsAtLocal() != null || request.getEndsAtLocal() != null) {
            LocalTime start = request.getStartsAtLocal() != null ? request.getStartsAtLocal() : slot.getStartTime();
            LocalTime end = request.getEndsAtLocal() != null ? request.getEndsAtLocal() : slot.getEndTime();
            if (!start.isBefore(end)) {
                throw new WorkshopValidationException("Slot start time must be strictly before end time");
            }
            List<WorkshopSlot> existingSlots = slotRepository.findByLocationId(location.getId());
            for (WorkshopSlot existing : existingSlots) {
                if (!existing.getId().equals(slot.getId()) && isOverlapping(start, end, existing.getStartTime(), existing.getEndTime())) {
                    throw new WorkshopSlotConflictException("Updated slot interval " + start + " - " + end +
                            " overlaps with configured slot '" + existing.getLabel() + "' (" +
                            existing.getStartTime() + " - " + existing.getEndTime() + ")");
                }
            }
            slot.setStartTime(start);
            slot.setEndTime(end);
        }

        WorkshopSlot saved = slotRepository.save(slot);
        return toSlotResponse(saved, location);
    }

    @Transactional(readOnly = true)
    public WorkshopSlot requireSlotEntityInScope(String authHeader, String slotIdentifier) {
        WorkshopSlot slot = resolveSlot(slotIdentifier);
        locationService.requireLocationEntityInScope(authHeader, slot.getLocation().getId());
        return slot;
    }

    private WorkshopSlot resolveSlot(String slotIdentifier) {
        try {
            Long numericId = Long.parseLong(slotIdentifier);
            return slotRepository.findById(numericId)
                    .orElseThrow(() -> new WorkshopNotFoundException("Workshop slot not found with ID " + numericId));
        } catch (NumberFormatException ignored) {
            return slotRepository.findByReference(slotIdentifier)
                    .orElseThrow(() -> new WorkshopNotFoundException("Workshop slot not found with reference " + slotIdentifier));
        }
    }

    private void validateSlotRequest(SlotRequest request) {
        if (request == null) {
            throw new WorkshopValidationException("Slot request must not be null");
        }
        if (request.getLabel() == null || request.getLabel().trim().isBlank()) {
            throw new WorkshopValidationException("Slot label is required");
        }
        if (request.getStartsAtLocal() == null || request.getEndsAtLocal() == null) {
            throw new WorkshopValidationException("Slot start and end times are required");
        }
        if (!request.isSameDayTimingValid()) {
            throw new WorkshopValidationException("Slot startsAtLocal must be strictly before endsAtLocal on the same day");
        }
        if (request.getDefaultCapacity() == null || request.getDefaultCapacity() < 1) {
            throw new WorkshopValidationException("Slot default capacity must be at least 1");
        }
    }

    private boolean isOverlapping(LocalTime start1, LocalTime end1,
                                  LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    private SlotResponse toSlotResponse(WorkshopSlot slot, WorkshopLocation location) {
        return SlotResponse.builder()
                .id(slot.getReference())
                .locationId(location.getId())
                .label(slot.getLabel())
                .startsAtLocal(slot.getStartTime())
                .endsAtLocal(slot.getEndTime())
                .defaultCapacity(slot.getDefaultCapacity())
                .active(slot.isActive())
                .build();
    }
}
