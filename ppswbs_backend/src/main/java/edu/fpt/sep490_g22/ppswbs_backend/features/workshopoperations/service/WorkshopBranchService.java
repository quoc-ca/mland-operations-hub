package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BranchRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BranchResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scoped Workshop Location/Branch management service.
 * Adapts to canonical WorkshopLocation repository.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopBranchService {

    private final WorkshopLocationRepository locationRepository;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final AccountFacade accountFacade;

    @Transactional(readOnly = true)
    public List<BranchResponse> listBranches(String authHeader) {
        AccountSummary actor = authorizationService.authenticate(authHeader);

        List<WorkshopLocation> locations;
        if (authorizationService.isAllBranchActor(actor)) {
            locations = locationRepository.findByActiveTrue();
        } else if (authorizationService.isManager(actor) || authorizationService.isStaff(actor)) {
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
                .map(this::toBranchResponse)
                .toList();
    }

    @Transactional
    public BranchResponse createBranch(String authHeader, BranchRequest request) {
        AccountSummary actor = authorizationService.requireAllBranchActor(authHeader);

        if (request == null) {
            throw new WorkshopValidationException("Branch request must not be null");
        }
        if (request.getCode() == null || request.getCode().trim().isBlank()) {
            throw new WorkshopValidationException("Branch code is required");
        }
        if (request.getName() == null || request.getName().trim().isBlank()) {
            throw new WorkshopValidationException("Branch name is required");
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

        return toBranchResponse(saved);
    }

    @Transactional(readOnly = true)
    public BranchResponse getBranchByReference(String authHeader, String reference) {
        WorkshopLocation location = requireLocationEntity(reference);
        AccountSummary actor = authorizationService.authenticate(authHeader);
        verifyLocationScope(actor, location);
        return toBranchResponse(location);
    }

    @Transactional(readOnly = true)
    public WorkshopLocation requireBranchEntityInScope(String authHeader, Long locationId) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        WorkshopLocation location = locationRepository.findById(locationId)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop location not found with ID " + locationId));

        verifyLocationScope(actor, location);
        return location;
    }

    @Transactional(readOnly = true)
    public WorkshopLocation requireBranchEntityInScopeByReference(String authHeader, String identifier) {
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
        if (authorizationService.isAllBranchActor(actor)) {
            return;
        }
        if (authorizationService.isManager(actor) || authorizationService.isStaff(actor)) {
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

    private BranchResponse toBranchResponse(WorkshopLocation location) {
        return BranchResponse.builder()
                .id(String.valueOf(location.getId()))
                .code(location.getCode())
                .name(location.getDisplayName())
                .address(location.getAddressSnapshot())
                .active(location.isActive())
                .build();
    }
}
