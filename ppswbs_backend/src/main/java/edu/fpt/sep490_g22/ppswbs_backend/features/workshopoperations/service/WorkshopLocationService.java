package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scoped Workshop Location management service for canonical WorkshopLocation resource.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopLocationService {

    private final WorkshopLocationRepository locationRepository;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final WorkshopVisibleLocationPolicy visibleLocationPolicy;
    private final WorkshopSessionRepository sessionRepository;
    private final OperationsAuditEventRepository auditEventRepository;

    private static final ObjectMapper AUDIT_JSON = new ObjectMapper();
    private static final Set<SessionState> ENDED_STATES = Set.of(SessionState.ATTENDANCE_FINALIZED, SessionState.CANCELLED);

    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations(String authHeader) {
        AccountSummary actor = authorizationService.authenticate(authHeader);

        List<WorkshopLocation> locations;
        if (authorizationService.isManager(actor)) {
            // FR-012c: a Manager sees every location, inactive ones included, so a deactivated one can be reactivated.
            locations = locationRepository.findAll(org.springframework.data.domain.Sort.by("id"));
        } else if (authorizationService.isStaff(actor)) {
            List<Long> locationScope = authorizationService.getLocationScope(actor.accountId());
            if (locationScope == null || locationScope.isEmpty()) {
                log.debug("Actor accountId={} has empty location scope; returning empty list",
                        actor.accountId());
                return List.of();
            }
            locations = locationRepository.findByIdInAndActiveTrue(locationScope);
        } else {
            log.warn("Access denied for accountId={}: actor cannot list locations",
                    actor.accountId());
            throw new WorkshopForbiddenException("Access denied: Actor cannot access workshop locations");
        }

        return locations.stream()
                .filter(location -> isVisibleLocation(location.getId()))
                .map(this::toLocationResponse)
                .toList();
    }

    private boolean isVisibleLocation(Long locationId) {
        return visibleLocationPolicy == null || visibleLocationPolicy.allows(locationId);
    }

    @Transactional
    public LocationResponse createLocation(String authHeader, LocationRequest request) {
        AccountSummary actor = authorizationService.requireGlobalManager(authHeader);

        if (request == null) {
            throw new WorkshopValidationException("Location request must not be null");
        }
        if (request.getCode() == null || request.getCode().trim().isBlank()) {
            throw new WorkshopValidationException("Location code is required");
        }
        if (request.getName() == null || request.getName().trim().isBlank()) {
            throw new WorkshopValidationException("Location name is required");
        }

        String normalizedCode = request.getCode().trim();
        if (locationRepository.findByCode(normalizedCode).isPresent()) {
            log.warn("Attempt to create location with duplicate code: {}", normalizedCode);
            throw new WorkshopValidationException("Location code '" + normalizedCode + "' already exists");
        }

        WorkshopLocation location = WorkshopLocation.builder()
                .code(normalizedCode)
                .displayName(request.getName().trim())
                .addressSnapshot(request.getAddress() != null ? request.getAddress().trim() : "")
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        WorkshopLocation saved = locationRepository.save(location);
        recordLocationAudit(saved.getId(), actor.accountId(), "LOCATION_CREATED", "location", null,
                java.util.Map.of("code", saved.getCode(), "name", saved.getDisplayName(), "address", saved.getAddressSnapshot(), "active", saved.isActive()));
        log.info("Created workshop location id={}, code={}, by all-location actor accountId={}",
                saved.getId(), saved.getCode(), actor.accountId());

        return toLocationResponse(saved);
    }

    /**
     * T135 (FR-012c): Manager renames a location, changes its address or deactivates/reactivates it.
     * {@code code} is immutable. Deactivation is refused with {@link WorkshopLifecycleException} (409 INVALID_STATE)
     * while a session not in ATTENDANCE_FINALIZED/CANCELLED uses the location; reactivation is always allowed.
     * One audit event is written per applied change.
     */
    @Transactional
    public LocationResponse updateLocation(String authHeader, Long locationId, LocationUpdateRequest request) {
        AccountSummary actor = authorizationService.requireGlobalManager(authHeader);

        if (request == null || (request.getName() == null && request.getAddress() == null && request.getActive() == null)) {
            throw new WorkshopValidationException("At least one of name, address or active is required");
        }
        if (request.getName() != null && request.getName().isBlank()) {
            throw new WorkshopValidationException("Location name must not be blank");
        }
        WorkshopLocation location = locationRepository.findById(locationId)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop location not found with ID " + locationId));
        if (request.getCode() != null && !request.getCode().trim().equals(location.getCode())) {
            throw new WorkshopValidationException("Location code is immutable");
        }

        String newName = request.getName() == null ? null : request.getName().trim();
        String newAddress = request.getAddress() == null ? null : request.getAddress().trim();
        boolean rename = newName != null && !newName.equals(location.getDisplayName());
        boolean addressChange = newAddress != null && !newAddress.equals(location.getAddressSnapshot());
        boolean activeChange = request.getActive() != null && request.getActive() != location.isActive();

        if (activeChange && !request.getActive()
                && sessionRepository.existsByLocationIdAndStateNotIn(location.getId(), ENDED_STATES)) {
            log.warn("Refused deactivation of locationId={}: sessions not yet ended still use it", location.getId());
            throw new WorkshopLifecycleException("Location cannot be deactivated while a session that has not ended"
                    + " (not ATTENDANCE_FINALIZED or CANCELLED) still uses it");
        }

        if (rename) {
            String prior = location.getDisplayName();
            location.setDisplayName(newName);
            recordLocationAudit(location.getId(), actor.accountId(), "LOCATION_RENAMED", "name", prior, newName);
        }
        if (addressChange) {
            String prior = location.getAddressSnapshot();
            location.setAddressSnapshot(newAddress);
            recordLocationAudit(location.getId(), actor.accountId(), "LOCATION_ADDRESS_CHANGED", "address", prior, newAddress);
        }
        if (activeChange) {
            boolean prior = location.isActive();
            location.setActive(request.getActive());
            recordLocationAudit(location.getId(), actor.accountId(),
                    request.getActive() ? "LOCATION_REACTIVATED" : "LOCATION_DEACTIVATED", "active", prior, request.getActive());
        }
        WorkshopLocation saved = rename || addressChange || activeChange ? locationRepository.save(location) : location;
        log.info("Updated workshop location id={} (rename={}, address={}, active={}) by Manager accountId={}",
                saved.getId(), rename, addressChange, activeChange, actor.accountId());
        return toLocationResponse(saved);
    }

    private void recordLocationAudit(Long locationId, Long actorId, String action, String field, Object prior, Object value) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("locationId", locationId);
        metadata.put("field", field);
        metadata.put("prior", prior);
        metadata.put("new", value);
        try {
            auditEventRepository.save(OperationsAuditEvent.builder()
                    .actorId(actorId)
                    .action(action)
                    .outcome("SUCCESS")
                    .correlationId(UUID.randomUUID().toString())
                    .metadata(AUDIT_JSON.writeValueAsString(metadata))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise location audit metadata", e);
        }
    }

    @Transactional(readOnly = true)
    public LocationResponse getLocationById(String authHeader, Long locationId) {
        WorkshopLocation location = requireLocationEntityInScope(authHeader, locationId);
        return toLocationResponse(location);
    }

    @Transactional(readOnly = true)
    public LocationResponse getLocationByReference(String authHeader, String reference) {
        WorkshopLocation location = requireLocationEntityByReference(authHeader, reference);
        return toLocationResponse(location);
    }

    @Transactional(readOnly = true)
    public WorkshopLocation requireLocationEntityInScope(String authHeader, Long locationId) {
        if (locationId == null) {
            throw new WorkshopValidationException("Location ID must not be null");
        }
        AccountSummary actor = authorizationService.authenticate(authHeader);
        WorkshopLocation location = locationRepository.findById(locationId)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop location not found with ID " + locationId));

        verifyLocationScope(actor, location);
        return location;
    }

    @Transactional(readOnly = true)
    public WorkshopLocation requireLocationEntityByReference(String authHeader, String identifier) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        WorkshopLocation location = requireLocationEntity(identifier);

        verifyLocationScope(actor, location);
        return location;
    }

    private WorkshopLocation requireLocationEntity(String identifier) {
        try {
            Long id = Long.parseLong(identifier);
            return locationRepository.findById(id)
                    .orElseThrow(() -> new WorkshopNotFoundException("Workshop location not found with ID " + identifier));
        } catch (NumberFormatException e) {
            return locationRepository.findByCode(identifier)
                    .orElseThrow(() -> new WorkshopNotFoundException("Workshop location not found with code " + identifier));
        }
    }

    private void verifyLocationScope(AccountSummary actor, WorkshopLocation location) {
        if (authorizationService.isManager(actor)) {
            return;
        }
        if (authorizationService.isStaff(actor)) {
            List<Long> locationScope = authorizationService.getLocationScope(actor.accountId());
            if (locationScope == null || !locationScope.contains(location.getId())) {
                log.warn("Location scope check failed: accountId={}, locationId={}, allowedScope={}",
                        actor.accountId(), location.getId(), locationScope);
                throw new WorkshopLocationScopeException("Location ID " + location.getId() + " is outside actor's active location scope");
            }
            return;
        }
        throw new WorkshopForbiddenException("Access denied: Actor cannot access workshop locations");
    }

    private LocationResponse toLocationResponse(WorkshopLocation location) {
        return LocationResponse.builder()
                .id(location.getId())
                .code(location.getCode())
                .name(location.getDisplayName())
                .address(location.getAddressSnapshot())
                .active(location.isActive())
                .build();
    }
}
