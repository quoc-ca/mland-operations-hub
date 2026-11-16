package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 003.2 Phase 18 (FR-037..FR-040): the one place that answers "is Package P sellable at location L" and the only
 * class that reads or writes the availability table. Sellable means the Package is {@code PUBLISHED}, its pair row
 * is {@code active} (a missing row is {@code INACTIVE}) and the location is active.
 */
@Service
@RequiredArgsConstructor
public class WorkshopPackageAvailabilityService {

    public static final String AUDIT_ACTION = "WORKSHOP_PACKAGE_AVAILABILITY_CHANGED";

    private final WorkshopPackageLocationAvailabilityRepository availabilityRepository;
    private final WorkshopPackageRepository packageRepository;
    private final WorkshopLocationRepository locationRepository;
    private final OperationsAuditEventRepository auditRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public boolean isSellable(Long packageId, Long locationId) {
        if (packageId == null || locationId == null) {
            return false;
        }
        boolean published = packageRepository.findById(packageId)
                .map(workshopPackage -> workshopPackage.getState() == WorkshopPackageState.PUBLISHED)
                .orElse(false);
        if (!published) {
            return false;
        }
        boolean pairActive = availabilityRepository.findByPackageIdAndLocationId(packageId, locationId)
                .map(WorkshopPackageLocationAvailability::isActive).orElse(false);
        return pairActive && locationRepository.findByIdAndActiveTrue(locationId).isPresent();
    }

    /** Ids of the active locations where the Package is sellable, ascending; empty for a non-published Package. */
    @Transactional(readOnly = true)
    public List<Long> activeLocationIds(Long packageId) {
        if (packageId == null) {
            return List.of();
        }
        boolean published = packageRepository.findById(packageId)
                .map(workshopPackage -> workshopPackage.getState() == WorkshopPackageState.PUBLISHED)
                .orElse(false);
        if (!published) {
            return List.of();
        }
        List<Long> pairLocationIds = availabilityRepository.findByPackageId(packageId).stream()
                .filter(WorkshopPackageLocationAvailability::isActive)
                .map(WorkshopPackageLocationAvailability::getLocationId)
                .toList();
        if (pairLocationIds.isEmpty()) {
            return List.of();
        }
        return locationRepository.findByIdInAndActiveTrue(pairLocationIds).stream()
                .map(WorkshopLocation::getId)
                .sorted()
                .toList();
    }

    /** Every Package x every location; a pair without a row is {@code INACTIVE} with version 0. */
    @Transactional(readOnly = true)
    public PackageAvailabilityDtos.Matrix readMatrix(Long actorId) {
        List<WorkshopPackage> packages = packageRepository.findAllByOrderByIdAsc();
        List<WorkshopLocation> locations = locationRepository.findAll().stream()
                .sorted(Comparator.comparing(WorkshopLocation::getId)).toList();
        Map<String, WorkshopPackageLocationAvailability> rows = new HashMap<>();
        for (WorkshopPackageLocationAvailability row : availabilityRepository.findAll()) {
            rows.put(key(row.getPackageId(), row.getLocationId()), row);
        }
        List<PackageAvailabilityDtos.Cell> cells = new ArrayList<>();
        for (WorkshopPackage workshopPackage : packages) {
            for (WorkshopLocation location : locations) {
                cells.add(toCell(workshopPackage.getId(), location.getId(),
                        rows.get(key(workshopPackage.getId(), location.getId()))));
            }
        }
        return new PackageAvailabilityDtos.Matrix(
                packages.stream().map(p -> new PackageAvailabilityDtos.PackageHeader(p.getId(), p.getName(),
                        p.getState().name())).toList(),
                locations.stream().map(l -> new PackageAvailabilityDtos.LocationHeader(l.getId(), l.getDisplayName(),
                        l.isActive())).toList(),
                cells);
    }

    /**
     * Sets one pair. The same state returns the current cell without a row write or audit event; a real change
     * writes the row and exactly one audit event in this transaction. Existing bookings, tickets, sessions and
     * holds are never read and never changed (FR-040).
     */
    @Transactional
    public PackageAvailabilityDtos.Cell setAvailability(Long actorId, Long packageId, Long locationId,
                                                        PackageAvailabilityDtos.State state, Long version,
                                                        String correlationReference) {
        if (packageRepository.findById(packageId).isEmpty()) {
            throw new WorkshopNotFoundException("Workshop Package not found");
        }
        if (!locationRepository.existsById(locationId)) {
            throw new WorkshopNotFoundException("Workshop Location not found");
        }
        boolean target = state == PackageAvailabilityDtos.State.ACTIVE;
        WorkshopPackageLocationAvailability row = availabilityRepository
                .findByPackageIdAndLocationId(packageId, locationId).orElse(null);
        boolean current = row != null && row.isActive();
        long storedVersion = row == null || row.getVersion() == null ? 0L : row.getVersion();
        // A stale version is a conflict even when the state would not change (contract: 409 OPTIMISTIC_LOCK_CONFLICT).
        if (version != null && version != storedVersion) {
            throw staleVersion();
        }
        if (current == target) {
            return toCell(packageId, locationId, row);
        }
        String account = String.valueOf(actorId);
        try {
            if (row == null) {
                row = availabilityRepository.saveAndFlush(WorkshopPackageLocationAvailability.builder()
                        .packageId(packageId).locationId(locationId).active(target)
                        .updatedByAccountReference(account).build());
            } else {
                row.setActive(target);
                row.setUpdatedByAccountReference(account);
                row = availabilityRepository.saveAndFlush(row);
            }
        } catch (DataIntegrityViolationException duplicate) {
            // Two Managers inserted the same missing pair at once: the loser sees a stale cell.
            throw staleVersion();
        }
        recordAudit(actorId, packageId, locationId, current, target, correlationReference);
        return toCell(packageId, locationId, row);
    }

    private void recordAudit(Long actorId, Long packageId, Long locationId, boolean from, boolean to,
                             String correlationReference) {
        String correlationId = correlationReference == null || correlationReference.isBlank()
                ? UUID.randomUUID().toString() : correlationReference;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("packageId", packageId);
        metadata.put("locationId", locationId);
        metadata.put("from", from ? "ACTIVE" : "INACTIVE");
        metadata.put("to", to ? "ACTIVE" : "INACTIVE");
        try {
            auditRepository.save(OperationsAuditEvent.builder()
                    .sessionId(null)
                    .actorId(actorId)
                    .action(AUDIT_ACTION)
                    .outcome("SUCCESS")
                    .correlationId(correlationId)
                    .metadata(objectMapper.writeValueAsString(metadata))
                    .build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize safe availability audit metadata", exception);
        }
    }

    private static ObjectOptimisticLockingFailureException staleVersion() {
        return new ObjectOptimisticLockingFailureException(WorkshopPackageLocationAvailability.class, null);
    }

    private static PackageAvailabilityDtos.Cell toCell(Long packageId, Long locationId,
                                                       WorkshopPackageLocationAvailability row) {
        boolean active = row != null && row.isActive();
        return new PackageAvailabilityDtos.Cell(packageId, locationId,
                active ? PackageAvailabilityDtos.State.ACTIVE : PackageAvailabilityDtos.State.INACTIVE,
                row == null || row.getVersion() == null ? 0L : row.getVersion());
    }

    private static String key(Long packageId, Long locationId) {
        return packageId + ":" + locationId;
    }
}
