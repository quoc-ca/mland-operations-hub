package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionOperationalLeadRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopOperationsAuthorizationService {

    private final AccountFacade accountFacade;
    private final SessionStaffAssignmentRepository staffAssignmentRepository;
    private final SessionOperationalLeadRepository operationalLeadRepository;
    private final WorkshopSessionRepository sessionRepository;

    @Value("${workshop.operations.auth.allow-test-tokens:false}")
    private boolean allowTestTokens;

    public AccountSummary authenticate(String authHeader) {
        String token = extractBearerToken(authHeader);
        String firebaseUid = verifyTokenAndGetUid(token);

        AccountSummary account = accountFacade.resolveAccount(firebaseUid)
            .orElseThrow(() -> new WorkshopForbiddenException("No registered Mland account found for verified identity"));

        if (account.status() == null || !"ACTIVE".equalsIgnoreCase(account.status().name())) {
            log.warn("Access denied for accountId={}: account status is {}", account.accountId(), account.status());
            throw new WorkshopForbiddenException("Account status is " + account.status() + ", ACTIVE status required");
        }

        return account;
    }

    public AccountSummary requireAllLocationActor(String authHeader) {
        AccountSummary account = authenticate(authHeader);

        if (!isAllLocationActor(account)) {
            log.warn("Access denied for accountId={}: expected all-location/higher-privilege actor, found role={}",
                    account.accountId(), account.role());
            throw new WorkshopForbiddenException("Access requires all-location higher-privilege authority");
        }

        return account;
    }

    public AccountSummary requireAllBranchActor(String authHeader) {
        return requireAllLocationActor(authHeader);
    }

    public boolean isAllLocationActor(AccountSummary account) {
        return account != null && account.role() != null && "ADMIN".equalsIgnoreCase(account.role().name());
    }

    /**
     * Workshop Operations V1 authority: an active Manager is global, while
     * ADMIN remains an all-location actor for compatibility.
     */
    public boolean isGlobalWorkshopActor(AccountSummary account) {
        return account != null && account.role() != null
                && ("ADMIN".equalsIgnoreCase(account.role().name())
                || "MANAGER".equalsIgnoreCase(account.role().name()));
    }

    public boolean isAllBranchActor(AccountSummary account) {
        return isAllLocationActor(account);
    }

    public boolean isManager(AccountSummary account) {
        return account != null && account.role() != null && "MANAGER".equalsIgnoreCase(account.role().name());
    }

    public boolean isStaff(AccountSummary account) {
        return account != null && account.role() != null && "STAFF".equalsIgnoreCase(account.role().name());
    }

    /**
     * Manager authority is global across locations throughout Feature 003.
     */
    public AccountSummary requireGlobalManager(String authHeader) {
        AccountSummary account = authenticate(authHeader);
        if (!isManager(account)) {
            log.warn("Access denied for accountId={}: expected MANAGER, found {}", account.accountId(), account.role());
            throw new WorkshopForbiddenException("Access requires MANAGER role");
        }
        return account;
    }

    public AccountSummary requireStaffSessionAssignment(String authHeader, Long sessionId) {
        AccountSummary account = authenticate(authHeader);

        WorkshopSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new WorkshopNotFoundException("Workshop session not found with ID " + sessionId));

        Long locationId = session.getLocation().getId();

        // Workshop Operations V1 does not require a per-session roster assignment.
        // Active Manager/Admin actors are global; Staff are authorized by active
        // account status plus direct membership in the session location scope.
        if (isGlobalWorkshopActor(account)) {
            return account;
        }

        if (isStaff(account)) {
            List<Long> locationScope = accountFacade.getLocationScope(account.accountId());
            if (locationScope == null || !locationScope.contains(locationId)) {
                log.warn("Staff location scope check failed for accountId={}, locationId={}, allowedScope={}",
                    account.accountId(), locationId, locationScope);
                throw new WorkshopLocationScopeException("Location ID " + locationId + " is outside Staff member's active location scope");
            }

            return account;
        }

        log.warn("Access denied for accountId={}, role={}: not an active Staff or global Manager/Admin",
            account.accountId(), account.role());
        throw new WorkshopForbiddenException("Access denied: Actor is not authorized for this workshop session");
    }

    public AccountSummary requireManagerOrAssignedStaff(String authHeader, Long sessionId, Long locationId) {
        AccountSummary account = authenticate(authHeader);

        if (isManager(account)) {
            return account;
        }

        if (isStaff(account)) {
            List<Long> locationScope = accountFacade.getLocationScope(account.accountId());
            if (locationScope == null || !locationScope.contains(locationId)) {
                throw new WorkshopLocationScopeException("Location ID " + locationId + " is outside Staff active location scope");
            }
            boolean assigned = staffAssignmentRepository.existsBySessionIdAndAccountIdAndActiveTrue(sessionId, account.accountId());
            if (!assigned) {
                throw new WorkshopForbiddenException("Staff member is not assigned to session ID " + sessionId);
            }
            return account;
        }

        throw new WorkshopForbiddenException("Access denied: Actor role " + account.role() + " cannot perform this operation");
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new WorkshopUnauthorizedException("Missing or malformed Authorization Bearer header");
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new WorkshopUnauthorizedException("Bearer token is empty");
        }
        return token;
    }

    private String verifyTokenAndGetUid(String token) {
        if (token.startsWith("test-uid:")) {
            if (!allowTestTokens) {
                log.warn("Rejecting test-uid token: test tokens are disabled in current environment");
                throw new WorkshopUnauthorizedException("Test tokens are disabled in this environment");
            }
            return token.substring("test-uid:".length());
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                throw new WorkshopUnauthorizedException("FirebaseApp is not configured");
            }
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token, true);
            return decodedToken.getUid();
        } catch (Exception e) {
            log.warn("Firebase token verification failed");
            throw new WorkshopUnauthorizedException("Invalid, expired or revoked identity token", e);
        }
    }
}
