package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal package-selection policy used by the public Operations facade.
 * Package and selected session rows are locked in the same transaction as reserve,
 * so stale customer discovery cannot authorize a now-invalid booking.
 */
@Service
@RequiredArgsConstructor
public class WorkshopPackageReservationPolicy {

    private final WorkshopPackageRepository packageRepository;
    private final WorkshopSessionRepository sessionRepository;
    private final WorkshopSlotRepository slotRepository;

    @Transactional(readOnly = true)
    public PublishedPackageSelectionSnapshot readPublishedSnapshot(PublishedPackageSelectionQuery query) {
        Long packageId = parsePackageReference(query.packageReference());
        if (packageId == null) {
            return null;
        }
        return packageRepository.findById(packageId)
                .filter(workshopPackage -> workshopPackage.getState() == WorkshopPackageState.PUBLISHED)
                .map(WorkshopPackageReservationPolicy::toSnapshot)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PublishedPackageSelectionSnapshot readPublishedSnapshot(Long packageId) {
        return packageRepository.findById(packageId)
                .filter(workshopPackage -> workshopPackage.getState() == WorkshopPackageState.PUBLISHED)
                .map(WorkshopPackageReservationPolicy::toSnapshot)
                .orElse(null);
    }

    public Long parsePackageReference(String packageReference) {
        if (packageReference == null || packageReference.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(packageReference);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Transactional
    public RejectionReason validate(ReserveBookingCapacityCommand command) {
        Long packageId = parsePackageReference(command.packageReference());
        if (packageId == null) {
            return RejectionReason.PACKAGE_SELECTION_INVALID;
        }
        WorkshopPackage workshopPackage = packageRepository.findByIdWithLock(packageId).orElse(null);
        if (workshopPackage == null || workshopPackage.getState() != WorkshopPackageState.PUBLISHED) {
            return RejectionReason.PACKAGE_NOT_PUBLISHED;
        }

        List<UUID> selectedReferences = command.orderedRequiredSessionReferences();
        if (command.participantCount() < workshopPackage.getMinParticipants()
                || command.participantCount() > workshopPackage.getMaxParticipants()
                || selectedReferences.size() != workshopPackage.getRequiredSessionCount()
                || selectedReferences.stream().distinct().count() != selectedReferences.size()) {
            return RejectionReason.PACKAGE_SELECTION_INVALID;
        }

        List<String> sortedReferences = selectedReferences.stream().map(UUID::toString).sorted().toList();
        List<WorkshopSession> locked = sessionRepository.findByReferenceInWithLock(sortedReferences);
        if (locked.size() != selectedReferences.size()) {
            return RejectionReason.PACKAGE_SELECTION_INVALID;
        }
        Map<String, WorkshopSession> byReference = new HashMap<>();
        locked.forEach(session -> byReference.put(session.getReference(), session));
        List<WorkshopSession> ordered = new ArrayList<>(selectedReferences.size());
        for (UUID reference : selectedReferences) {
            WorkshopSession session = byReference.get(reference.toString());
            if (session == null) {
                return RejectionReason.PACKAGE_SELECTION_INVALID;
            }
            ordered.add(session);
        }

        if (workshopPackage.isSameLocationRequired()) {
            long locationCount = ordered.stream().map(session -> session.getLocation().getId()).distinct().count();
            if (locationCount != 1) {
                return RejectionReason.PACKAGE_SELECTION_INVALID;
            }
        }

        Instant now = Instant.now();
        if (ordered.stream().anyMatch(session -> session.getState() != SessionState.PUBLISHED
                || session.getScheduledStartAt() == null || !session.getScheduledStartAt().isAfter(now))) {
            return RejectionReason.SALE_NOT_ELIGIBLE;
        }

        RejectionReason gapRejection = validateSameDaySlotGap(ordered,
                workshopPackage.getMinimumSameDaySlotGap());
        if (gapRejection != null) {
            return gapRejection;
        }
        return null;
    }

    private RejectionReason validateSameDaySlotGap(List<WorkshopSession> sessions, int minimumGap) {
        if (minimumGap <= 0) {
            return null;
        }
        for (int left = 0; left < sessions.size(); left++) {
            for (int right = left + 1; right < sessions.size(); right++) {
                WorkshopSession first = sessions.get(left);
                WorkshopSession second = sessions.get(right);
                if (!first.getWorkshopDate().equals(second.getWorkshopDate())) {
                    continue;
                }
                Long locationId = first.getLocation().getId();
                if (!locationId.equals(second.getLocation().getId())) {
                    continue;
                }
                List<WorkshopSlot> configuredSlots = slotRepository.findByLocationId(locationId).stream()
                        .sorted(Comparator.comparing(WorkshopSlot::getStartTime).thenComparing(WorkshopSlot::getId))
                        .toList();
                int firstIndex = slotIndex(configuredSlots, first.getSlot().getId());
                int secondIndex = slotIndex(configuredSlots, second.getSlot().getId());
                if (firstIndex < 0 || secondIndex < 0
                        || Math.abs(firstIndex - secondIndex) - 1 < minimumGap) {
                    return RejectionReason.PACKAGE_SELECTION_INVALID;
                }
            }
        }
        return null;
    }

    private static int slotIndex(List<WorkshopSlot> slots, Long slotId) {
        for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index).getId().equals(slotId)) {
                return index;
            }
        }
        return -1;
    }

    private static PublishedPackageSelectionSnapshot toSnapshot(WorkshopPackage workshopPackage) {
        return new PublishedPackageSelectionSnapshot(
                workshopPackage.getId().toString(),
                workshopPackage.getPublicPrice(),
                workshopPackage.getMinParticipants(),
                workshopPackage.getMaxParticipants(),
                workshopPackage.getRequiredSessionCount(),
                workshopPackage.isSameLocationRequired(),
                workshopPackage.getMinimumSameDaySlotGap(),
                workshopPackage.getName());
    }
}
