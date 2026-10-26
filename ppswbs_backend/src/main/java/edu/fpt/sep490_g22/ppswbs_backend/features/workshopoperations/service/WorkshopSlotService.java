package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBranch;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * T016: Service managing reusable branch slots.
 * <p>
 * Enforces:
 * - Scoped Manager access for slot configuration.
 * - Non-overlapping slot intervals against all configured slots in the branch (including inactive slots).
 * - No hard-deletes (only active/inactive state changes).
 * - Slot changes apply only to later created sessions, preserving existing session schedule snapshots.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopSlotService {

    private final WorkshopSlotRepository slotRepository;
    private final WorkshopBranchService branchService;

    /**
     * List all slots for a given workshop branch within actor's scope.
     *
     * @param authHeader       Bearer authorization header
     * @param branchIdentifier Branch UUID reference or database ID
     * @return List of SlotResponse
     */
    @Transactional(readOnly = true)
    public List<SlotResponse> listSlots(String authHeader, String branchIdentifier) {
        WorkshopBranch branch = resolveBranch(authHeader, branchIdentifier);
        List<WorkshopSlot> slots = slotRepository.findByBranchId(branch.getId());

        return slots.stream()
                .map(s -> toSlotResponse(s, branch))
                .toList();
    }

    /**
     * Create a new non-overlapping slot in the specified branch.
     *
     * @param authHeader       Bearer authorization header
     * @param branchIdentifier Branch UUID reference or database ID
     * @param request          Slot creation request
     * @return Created SlotResponse
     */
    @Transactional
    public SlotResponse createSlot(String authHeader, String branchIdentifier, SlotRequest request) {
        WorkshopBranch branch = resolveBranch(authHeader, branchIdentifier);
        validateSlotRequest(request);

        // Check overlap against every configured slot in the branch (including inactive slots)
        List<WorkshopSlot> existingSlots = slotRepository.findByBranchId(branch.getId());
        for (WorkshopSlot existing : existingSlots) {
            if (isOverlapping(request.getStartsAtLocal(), request.getEndsAtLocal(),
                    existing.getStartTime(), existing.getEndTime())) {
                log.warn("Slot overlap conflict: requested {}-{} overlaps with existing slot '{}' ({}-{}) in branch {}",
                        request.getStartsAtLocal(), request.getEndsAtLocal(),
                        existing.getLabel(), existing.getStartTime(), existing.getEndTime(), branch.getCode());
                throw new WorkshopSlotConflictException("Slot interval " + request.getStartsAtLocal() + " - " +
                        request.getEndsAtLocal() + " overlaps with configured slot '" + existing.getLabel() + "' (" +
                        existing.getStartTime() + " - " + existing.getEndTime() + ")");
            }
        }

        WorkshopSlot slot = WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .branch(branch)
                .label(request.getLabel().trim())
                .startTime(request.getStartsAtLocal())
                .endTime(request.getEndsAtLocal())
                .defaultCapacity(request.getDefaultCapacity())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        WorkshopSlot saved = slotRepository.save(slot);
        log.info("Created workshop slot id={}, reference={}, label={}, branchId={}",
                saved.getId(), saved.getReference(), saved.getLabel(), branch.getId());

        return toSlotResponse(saved, branch);
    }

    /**
     * Edit a slot or change its active flag.
     * Overlap is rejected against all other slots in the branch (including inactive ones).
     * Modifying a slot does NOT rewrite schedules of existing sessions.
     *
     * @param authHeader     Bearer authorization header
     * @param slotIdentifier Slot UUID reference or database ID
     * @param request        Slot edit request
     * @return Updated SlotResponse
     */
    @Transactional
    public SlotResponse updateSlot(String authHeader, String slotIdentifier, SlotRequest request) {
        WorkshopSlot slot = resolveSlot(slotIdentifier);
        WorkshopBranch branch = slot.getBranch();

        // Enforce Manager branch scope
        branchService.requireBranchEntityInScope(authHeader, branch.getId());
        validateSlotRequest(request);

        // Overlap check against all other slots in the branch (both active and inactive)
        List<WorkshopSlot> existingSlots = slotRepository.findByBranchId(branch.getId());
        for (WorkshopSlot existing : existingSlots) {
            if (!existing.getId().equals(slot.getId())) {
                if (isOverlapping(request.getStartsAtLocal(), request.getEndsAtLocal(),
                        existing.getStartTime(), existing.getEndTime())) {
                    log.warn("Slot update overlap conflict: requested {}-{} overlaps with slot '{}' ({}-{})",
                            request.getStartsAtLocal(), request.getEndsAtLocal(),
                            existing.getLabel(), existing.getStartTime(), existing.getEndTime());
                    throw new WorkshopSlotConflictException("Slot interval " + request.getStartsAtLocal() + " - " +
                            request.getEndsAtLocal() + " overlaps with configured slot '" + existing.getLabel() + "' (" +
                            existing.getStartTime() + " - " + existing.getEndTime() + ")");
                }
            }
        }

        if (request.getLabel() != null && !request.getLabel().trim().isBlank()) {
            slot.setLabel(request.getLabel().trim());
        }
        slot.setStartTime(request.getStartsAtLocal());
        slot.setEndTime(request.getEndsAtLocal());
        slot.setDefaultCapacity(request.getDefaultCapacity());
        if (request.getActive() != null) {
            slot.setActive(request.getActive());
        }

        WorkshopSlot updated = slotRepository.save(slot);
        log.info("Updated workshop slot id={}, reference={}, active={}",
                updated.getId(), updated.getReference(), updated.isActive());

        return toSlotResponse(updated, branch);
    }

    /**
     * Retrieve slot entity by reference or ID within actor's scope.
     *
     * @param authHeader     Bearer authorization header
     * @param slotIdentifier Slot UUID reference or database ID
     * @return WorkshopSlot entity
     */
    @Transactional(readOnly = true)
    public WorkshopSlot requireSlotEntityInScope(String authHeader, String slotIdentifier) {
        WorkshopSlot slot = resolveSlot(slotIdentifier);
        branchService.requireBranchEntityInScope(authHeader, slot.getBranch().getId());
        return slot;
    }

    private WorkshopBranch resolveBranch(String authHeader, String branchIdentifier) {
        try {
            Long numericId = Long.parseLong(branchIdentifier);
            return branchService.requireBranchEntityInScope(authHeader, numericId);
        } catch (NumberFormatException ignored) {
            return branchService.requireBranchEntityInScopeByReference(authHeader, branchIdentifier);
        }
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

    private boolean isOverlapping(java.time.LocalTime start1, java.time.LocalTime end1,
                                  java.time.LocalTime start2, java.time.LocalTime end2) {
        // Two time intervals [start1, end1) and [start2, end2) overlap if start1 < end2 AND end1 > start2
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    private SlotResponse toSlotResponse(WorkshopSlot slot, WorkshopBranch branch) {
        return SlotResponse.builder()
                .id(slot.getReference())
                .branchId(branch.getReference())
                .label(slot.getLabel())
                .startsAtLocal(slot.getStartTime())
                .endsAtLocal(slot.getEndTime())
                .defaultCapacity(slot.getDefaultCapacity())
                .active(slot.isActive())
                .build();
    }
}
