package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopBookingPackagePolicySnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBookingPackagePolicySnapshot;
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
    private final WorkshopBookingPackagePolicySnapshotRepository bookingPolicySnapshotRepository;

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

    /** Returns the package terms that Operations must freeze for this booking.
     * The caller invokes this after validation in the same transaction, while the
     * package row is still protected by the validation lock.
     */
    @Transactional
    public WorkshopBookingPackagePolicySnapshot snapshotForBooking(ReserveBookingCapacityCommand command) {
        Long packageId = parsePackageReference(command.packageReference());
        if (packageId == null) return null;
        WorkshopPackage workshopPackage = packageRepository.findByIdWithLock(packageId).orElse(null);
        if (workshopPackage == null || workshopPackage.getState() != WorkshopPackageState.PUBLISHED) return null;
        return WorkshopBookingPackagePolicySnapshot.builder()
                .bookingReference(command.bookingReference())
                .packageReference(workshopPackage.getId().toString())
                .requiredSessionCount(workshopPackage.getRequiredSessionCount())
                .minParticipants(workshopPackage.getMinParticipants())
                .maxParticipants(workshopPackage.getMaxParticipants())
                .sameLocationRequired(workshopPackage.isSameLocationRequired())
                .minimumSameDaySlotGap(workshopPackage.getMinimumSameDaySlotGap())
                .build();
    }

    /** Fails closed for legacy bookings without an authoritative booking-time snapshot. */
    @Transactional(readOnly = true)
    public boolean isValidReplacement(String bookingReference, List<WorkshopSession> targets, int participantCount) {
        WorkshopBookingPackagePolicySnapshot snapshot = bookingPolicySnapshotRepository
                .findByBookingReference(bookingReference).orElse(null);
        if (snapshot == null || targets.size() != snapshot.getRequiredSessionCount()
                || participantCount < snapshot.getMinParticipants()
                || participantCount > snapshot.getMaxParticipants()) return false;

        for (int i = 1; i < targets.size(); i++) {
            if (!targets.get(i - 1).getScheduledStartAt().isBefore(targets.get(i).getScheduledStartAt())) return false;
        }
        if (snapshot.isSameLocationRequired()
                && targets.stream().map(s -> s.getLocation().getId()).distinct().count() != 1) return false;

        int minimumGap = snapshot.getMinimumSameDaySlotGap();
        if (minimumGap <= 0) return true;
        Map<Long, List<WorkshopSlot>> slotsByLocation = new HashMap<>();
        for (int left = 0; left < targets.size(); left++) {
            for (int right = left + 1; right < targets.size(); right++) {
                WorkshopSession first = targets.get(left);
                WorkshopSession second = targets.get(right);
                if (!first.getWorkshopDate().equals(second.getWorkshopDate())
                        || !first.getLocation().getId().equals(second.getLocation().getId())) continue;
                List<WorkshopSlot> slots = slotsByLocation.computeIfAbsent(first.getLocation().getId(), id ->
                        slotRepository.findByLocationId(id).stream()
                                .sorted(Comparator.comparing(WorkshopSlot::getStartTime).thenComparing(WorkshopSlot::getId))
                                .toList());
                int firstIndex = slotIndex(slots, first.getSlot().getId());
                int secondIndex = slotIndex(slots, second.getSlot().getId());
                if (firstIndex < 0 || secondIndex < 0 || Math.abs(firstIndex - secondIndex) - 1 < minimumGap) return false;
            }
        }
        return true;
    }

    /**
     * Validates the current Basic Ring package configuration for OTA acceptance.
     * The editable package name is not used as a type discriminator; the supported
     * configuration is one required session, same-location and no same-day gap.
     */
    @Transactional
    public RejectionReason validateOtaBooking(String packageReference, UUID sessionReference, int participantCount) {
        Long packageId = parsePackageReference(packageReference);
        if (packageId == null) {
            return RejectionReason.PACKAGE_SELECTION_INVALID;
        }
        WorkshopPackage workshopPackage = packageRepository.findByIdWithLock(packageId).orElse(null);
        if (workshopPackage == null || workshopPackage.getState() != WorkshopPackageState.PUBLISHED) {
            return RejectionReason.PACKAGE_NOT_PUBLISHED;
        }
        if (workshopPackage.getRequiredSessionCount() != 1
                || !workshopPackage.isSameLocationRequired()
                || workshopPackage.getMinimumSameDaySlotGap() != 0
                || participantCount < workshopPackage.getMinParticipants()
                || participantCount > workshopPackage.getMaxParticipants()) {
            return RejectionReason.PACKAGE_SELECTION_INVALID;
        }

        List<WorkshopSession> lockedSessions = sessionRepository.findByReferenceInWithLock(
                List.of(sessionReference.toString()));
        if (lockedSessions.size() != 1) {
            return RejectionReason.PACKAGE_SELECTION_INVALID;
        }
        WorkshopSession session = lockedSessions.getFirst();
        if (session.getState() != SessionState.PUBLISHED || session.getScheduledStartAt() == null
                || !session.getScheduledStartAt().isAfter(Instant.now())) {
            return RejectionReason.SALE_NOT_ELIGIBLE;
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
