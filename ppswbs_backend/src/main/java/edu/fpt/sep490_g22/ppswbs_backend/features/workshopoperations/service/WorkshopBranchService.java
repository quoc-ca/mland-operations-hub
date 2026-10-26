package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopBranchRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BranchRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BranchResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBranch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * T015: Scoped Workshop Branch management service.
 * <p>
 * Handles branch listing and creation within canonical Account Management boundaries.
 * Enforces that only all-branch / higher-privilege actors create branches; Managers only
 * list and read branches within their assigned location scope.
 * Preserves branch history (no hard-deletes, no silent historical edits) and never creates
 * or infers Manager-to-branch assignments within Operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopBranchService {

    private final WorkshopBranchRepository branchRepository;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final AccountFacade accountFacade;

    /**
     * List active workshop branches visible to the authenticated actor.
     * <p>
     * - All-branch actor (ADMIN): returns all active branches.
     * - Scoped actor (MANAGER / STAFF): returns only active branches within their canonical location scope.
     * - Other roles (e.g. MEMBER): denied with {@link WorkshopForbiddenException}.
     *
     * @param authHeader Bearer authorization header
     * @return List of branch responses matching OpenAPI schema
     */
    @Transactional(readOnly = true)
    public List<BranchResponse> listBranches(String authHeader) {
        AccountSummary actor = authorizationService.authenticate(authHeader);

        List<WorkshopBranch> branches;
        if (authorizationService.isAllBranchActor(actor)) {
            branches = branchRepository.findByActiveTrue();
        } else if (authorizationService.isManager(actor) || authorizationService.isStaff(actor)) {
            List<Long> locationScope = accountFacade.getLocationScope(actor.accountId());
            if (locationScope == null || locationScope.isEmpty()) {
                log.debug("Actor accountId={} has empty location scope; returning empty branch list",
                        actor.accountId());
                return List.of();
            }
            branches = branchRepository.findByIdInAndActiveTrue(locationScope);
        } else {
            log.warn("Access denied for accountId={}: actor cannot list workshop branches",
                    actor.accountId());
            throw new WorkshopForbiddenException("Access denied: Actor cannot access workshop branches");
        }

        return branches.stream()
                .map(this::toBranchResponse)
                .toList();
    }

    /**
     * Create a new workshop branch.
     * <p>
     * - Only all-branch / higher-privilege actors (ADMIN) can create workshop branches.
     * - Branch-scoped Managers cannot create branches (they only manage branches assigned
     *   to them by Account Management; Operations does not create or infer assignments).
     * - Rejects duplicate codes to preserve branch history and avoid overwriting existing records.
     *
     * @param authHeader Bearer authorization header
     * @param request    Branch creation request
     * @return Created branch response with public UUID reference
     */
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
        if (branchRepository.findByCode(normalizedCode).isPresent()) {
            log.warn("Attempt to create branch with duplicate code: {}", normalizedCode);
            throw new WorkshopValidationException("Branch code '" + normalizedCode + "' already exists");
        }

        WorkshopBranch branch = WorkshopBranch.builder()
                .reference(UUID.randomUUID().toString())
                .code(normalizedCode)
                .displayName(request.getName().trim())
                .addressSnapshot(request.getAddress() != null ? request.getAddress().trim() : "")
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        WorkshopBranch saved = branchRepository.save(branch);
        log.info("Created workshop branch id={}, reference={}, code={}, by all-branch actor accountId={}",
                saved.getId(), saved.getReference(), saved.getCode(), actor.accountId());

        return toBranchResponse(saved);
    }

    /**
     * Retrieve a branch response by public UUID reference within the actor's scope.
     *
     * @param authHeader Bearer authorization header
     * @param reference  Public UUID reference of the branch
     * @return BranchResponse matching OpenAPI schema
     */
    @Transactional(readOnly = true)
    public BranchResponse getBranchByReference(String authHeader, String reference) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        WorkshopBranch branch = branchRepository.findByReference(reference)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop branch not found with reference " + reference));

        verifyBranchScope(actor, branch);
        return toBranchResponse(branch);
    }

    /**
     * Retrieve a JPA entity by database primary key within the actor's scope.
     * Intended for internal module use (e.g. slot/session services).
     *
     * @param authHeader Bearer authorization header
     * @param branchId   Database primary key of the branch
     * @return WorkshopBranch entity
     */
    @Transactional(readOnly = true)
    public WorkshopBranch requireBranchEntityInScope(String authHeader, Long branchId) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        WorkshopBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop branch not found with ID " + branchId));

        verifyBranchScope(actor, branch);
        return branch;
    }

    /**
     * Retrieve a JPA entity by public UUID reference within the actor's scope.
     * Intended for internal module use (e.g. slot/session services).
     *
     * @param authHeader Bearer authorization header
     * @param reference  Public UUID reference of the branch
     * @return WorkshopBranch entity
     */
    @Transactional(readOnly = true)
    public WorkshopBranch requireBranchEntityInScopeByReference(String authHeader, String reference) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        WorkshopBranch branch = branchRepository.findByReference(reference)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop branch not found with reference " + reference));

        verifyBranchScope(actor, branch);
        return branch;
    }

    private void verifyBranchScope(AccountSummary actor, WorkshopBranch branch) {
        if (authorizationService.isAllBranchActor(actor)) {
            return;
        }
        if (authorizationService.isManager(actor) || authorizationService.isStaff(actor)) {
            List<Long> locationScope = accountFacade.getLocationScope(actor.accountId());
            if (locationScope == null || !locationScope.contains(branch.getId())) {
                log.warn("Branch scope check failed: accountId={}, branchId={}, allowedScope={}",
                        actor.accountId(), branch.getId(), locationScope);
                throw new WorkshopBranchScopeException("Branch ID " + branch.getId() + " is outside actor's active location scope");
            }
            return;
        }
        throw new WorkshopForbiddenException("Access denied: Actor cannot access workshop branches");
    }

    private BranchResponse toBranchResponse(WorkshopBranch branch) {
        return BranchResponse.builder()
                .id(branch.getReference())
                .code(branch.getCode())
                .name(branch.getDisplayName())
                .address(branch.getAddressSnapshot())
                .active(branch.isActive())
                .build();
    }
}
