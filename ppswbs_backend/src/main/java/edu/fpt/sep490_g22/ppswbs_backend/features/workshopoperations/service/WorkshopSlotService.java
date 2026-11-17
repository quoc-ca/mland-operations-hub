package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final OperationsAuditEventRepository auditEventRepository;

    private static final ObjectMapper AUDIT_JSON = new ObjectMapper();

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
        authorizationService.requireGlobalManager(authHeader); // FR-001e: Manager only
        WorkshopLocation location = locationService.requireLocationEntityInScope(authHeader, locationId);
        validateSlotRequest(request);

        boolean willBeActive = request.getActive() != null ? request.getActive() : true;
        if (willBeActive) {
            requireNoActiveOverlap(location, null, request.getStartsAtLocal(), request.getEndsAtLocal());
        }

        WorkshopSlot slot = WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(location)
                .label(request.getLabel().trim())
                .startTime(request.getStartsAtLocal())
                .endTime(request.getEndsAtLocal())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        WorkshopSlot saved = slotRepository.save(slot);
        recordSlotAudit(authHeader, "SLOT_CREATED", saved, null, slotValues(saved));
        log.info("Created slot id={}, reference={}, label='{}' in location id={}",
                saved.getId(), saved.getReference(), saved.getLabel(), location.getId());

        return toSlotResponse(saved, location);
    }

    @Transactional
    public SlotResponse updateSlot(String authHeader, String slotIdentifier, SlotRequest request) {
        authorizationService.requireGlobalManager(authHeader); // FR-001e: Manager only
        WorkshopSlot slot = resolveSlot(slotIdentifier);
        WorkshopLocation location = locationService.requireLocationEntityInScope(authHeader, slot.getLocation().getId());

        if (request == null) {
            throw new WorkshopValidationException("Slot patch request must not be null");
        }

        Map<String, Object> prior = slotValues(slot);
        boolean priorActive = slot.isActive();
        if (request.getLabel() != null && !request.getLabel().trim().isBlank()) {
            slot.setLabel(request.getLabel().trim());
        }


        boolean timesChanged = request.getStartsAtLocal() != null || request.getEndsAtLocal() != null;
        LocalTime start = request.getStartsAtLocal() != null ? request.getStartsAtLocal() : slot.getStartTime();
        LocalTime end = request.getEndsAtLocal() != null ? request.getEndsAtLocal() : slot.getEndTime();
        if (timesChanged && !start.isBefore(end)) {
            throw new WorkshopValidationException("Slot start time must be strictly before end time");
        }
        boolean willBeActive = request.getActive() != null ? request.getActive() : priorActive;
        boolean reactivated = willBeActive && !priorActive;
        // Only ACTIVE slots hold their time: an inactive slot neither blocks others nor needs a check itself.
        if (willBeActive && (timesChanged || reactivated)) {
            requireNoActiveOverlap(location, slot.getId(), start, end);
        }
        slot.setActive(willBeActive);
        if (timesChanged) {
            slot.setStartTime(start);
            slot.setEndTime(end);
        }

        WorkshopSlot saved = slotRepository.save(slot);
        Map<String, Object> current = slotValues(saved);
        Map<String, Object> priorOther = new LinkedHashMap<>(prior);
        Map<String, Object> currentOther = new LinkedHashMap<>(current);
        priorOther.remove("active");
        currentOther.remove("active");
        if (!priorOther.equals(currentOther)) {
            recordSlotAudit(authHeader, "SLOT_UPDATED", saved, priorOther, currentOther);
        }
        if (priorActive != saved.isActive()) {
            recordSlotAudit(authHeader, saved.isActive() ? "SLOT_ACTIVATED" : "SLOT_DEACTIVATED", saved,
                    Map.of("active", priorActive), Map.of("active", saved.isActive()));
        }
        return toSlotResponse(saved, location);
    }


    private static Map<String, Object> slotValues(WorkshopSlot slot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("label", slot.getLabel());
        values.put("startsAtLocal", String.valueOf(slot.getStartTime()));
        values.put("endsAtLocal", String.valueOf(slot.getEndTime()));
        values.put("active", slot.isActive());
        return values;
    }

    /** FR-007c: one audit event per applied slot change, with the prior and new values; not tied to a session. */
    private void recordSlotAudit(String authHeader, String action, WorkshopSlot slot, Map<String, Object> prior, Map<String, Object> value) {
        Long actorId = authorizationService.authenticate(authHeader).accountId();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("slotReference", slot.getReference());
        metadata.put("locationId", slot.getLocation().getId());
        metadata.put("prior", prior);
        metadata.put("new", value);
        try {
            auditEventRepository.save(OperationsAuditEvent.builder().actorId(actorId).action(action).outcome("SUCCESS")
                    .correlationId(UUID.randomUUID().toString()).metadata(AUDIT_JSON.writeValueAsString(metadata)).build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise slot audit metadata", e);
        }
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
    }

    /** FR-001j: slots of one location must not overlap; only ACTIVE slots are compared (inactive ones hold no time). */
    private void requireNoActiveOverlap(WorkshopLocation location, Long selfId, LocalTime start, LocalTime end) {
        for (WorkshopSlot existing : slotRepository.findByLocationIdAndActiveTrue(location.getId())) {
            if (!existing.getId().equals(selfId)
                    && isOverlapping(start, end, existing.getStartTime(), existing.getEndTime())) {
                log.warn("Slot overlap conflict: {}-{} overlaps active slot '{}' ({}-{}) in location {}",
                        start, end, existing.getLabel(), existing.getStartTime(), existing.getEndTime(), location.getCode());
                throw new WorkshopSlotConflictException("Slot interval " + start + " - " + end +
                        " overlaps with active slot '" + existing.getLabel() + "' (" +
                        existing.getStartTime() + " - " + existing.getEndTime() + ")");
            }
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
                .active(slot.isActive())
                .build();
    }
}
