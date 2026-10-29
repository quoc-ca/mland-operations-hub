package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scoped Workshop Location management service for canonical WorkshopLocation resource.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopLocationService {

    private final WorkshopLocationRepository locationRepository;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final AccountFacade accountFacade;

    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations(String authHeader) {
        AccountSummary actor = authorizationService.authenticate(authHeader);

        List<WorkshopLocation> locations;
        if (authorizationService.isAllBranchActor(actor) || authorizationService.isManager(actor)) {
            locations = locationRepository.findByActiveTrue();
        } else if (authorizationService.isStaff(actor)) {
            List<Long> locationScope = accountFacade.getLocationScope(actor.accountId());
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
                .map(this::toLocationResponse)
                .toList();
    }

    @Transactional
    public LocationResponse createLocation(String authHeader, LocationRequest request) {
        AccountSummary actor = authorizationService.requireAllBranchActor(authHeader);

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
        log.info("Created workshop location id={}, code={}, by all-location actor accountId={}",
                saved.getId(), saved.getCode(), actor.accountId());

        return toLocationResponse(saved);
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
        if (authorizationService.isAllBranchActor(actor) || authorizationService.isManager(actor)) {
            return;
        }
        if (authorizationService.isStaff(actor)) {
            List<Long> locationScope = accountFacade.getLocationScope(actor.accountId());
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
