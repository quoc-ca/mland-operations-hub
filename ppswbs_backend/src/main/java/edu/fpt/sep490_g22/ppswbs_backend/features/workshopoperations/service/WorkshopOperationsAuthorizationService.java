package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.RosterOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.TargetAccountSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopOperationsAuthorizationService {

    private final AccountFacade accountFacade;
    private final SessionStaffAssignmentRepository staffAssignmentRepository;
    private final WorkshopSessionRepository sessionRepository;

    @Value("${workshop.operations.auth.allow-test-tokens:false}")
    private boolean allowTestTokens;

    public AccountSummary authenticate(String authHeader) {
        String token = extractBearerToken(authHeader);
        String firebaseUid = verifyTokenAndGetUid(token);

        AccountSummary account = resolveAccount(firebaseUid)
            .orElseThrow(() -> new WorkshopForbiddenException("No registered Mland account found for verified identity"));

        if (account.status() == null || !"ACTIVE".equalsIgnoreCase(account.status().name())) {
            log.warn("Access denied for accountId={}: account status is {}", account.accountId(), account.status());
            throw new WorkshopForbiddenException("Account status is " + account.status() + ", ACTIVE status required");
        }

        return account;
    }

    /**
     * Workshop Operations V1 authority: only an active Manager is global (FR-012c). ADMIN holds no business
     * authority in this feature; it is refused with 403 like any other non-Manager, non-assigned-Staff role.
     */
    public boolean isGlobalWorkshopActor(AccountSummary account) {
        return isManager(account);
    }

    public boolean isManager(AccountSummary account) {
        return account != null && account.role() != null && "MANAGER".equalsIgnoreCase(account.role().name());
    }

    public boolean isAdmin(AccountSummary account) {
        return account != null && account.role() != null && "ADMIN".equalsIgnoreCase(account.role().name());
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

    /** A Staff member may act on a session only while actively assigned to it. */
    public void requireAssignment(AccountSummary staff, Long sessionId) {
        if (!staffAssignmentRepository.existsBySessionIdAndAccountIdAndActiveTrue(sessionId, staff.accountId())) {
            log.warn("Staff accountId={} is not assigned to sessionId={}", staff.accountId(), sessionId);
            throw new WorkshopForbiddenException("Access denied: Staff member is not assigned to this workshop session");
        }
    }

    public AccountSummary requireStaffSessionAssignment(String authHeader, Long sessionId) {
        AccountSummary account = authenticate(authHeader);

        WorkshopSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new WorkshopNotFoundException("Workshop session not found with ID " + sessionId));

        Long locationId = session.getLocation().getId();

        // An active Manager is global. Staff are authorized by active account status, membership in the
        // session location scope AND an active assignment to this session (owner decision 2026-11-06: a Staff
        // sees and operates only the sessions they are assigned to).
        if (isGlobalWorkshopActor(account)) {
            return account;
        }

        if (isStaff(account)) {
            List<Long> locationScope = getLocationScope(account.accountId());
            if (locationScope == null || !locationScope.contains(locationId)) {
                log.warn("Staff location scope check failed for accountId={}, locationId={}, allowedScope={}",
                    account.accountId(), locationId, locationScope);
                throw new WorkshopLocationScopeException("Location ID " + locationId + " is outside Staff member's active location scope");
            }
            requireAssignment(account, sessionId);

            return account;
        }

        log.warn("Access denied for accountId={}, role={}: not an active Staff or global Manager",
            account.accountId(), account.role());
        throw new WorkshopForbiddenException("Access denied: Actor is not authorized for this workshop session");
    }

    public AccountSummary requireManagerOrAssignedStaff(String authHeader, Long sessionId, Long locationId) {
        AccountSummary account = authenticate(authHeader);

        if (isManager(account)) {
            return account;
        }

        if (isStaff(account)) {
            List<Long> locationScope = getLocationScope(account.accountId());
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

    // ---- Account facade access: fail closed with 503 DEPENDENCY_UNAVAILABLE (T128, FR-012a) -------------------
    // No cache and no fallback: an exception from the facade never turns into a grant or a plain 403/500.

    public Optional<AccountSummary> resolveAccount(String firebaseUid) {
        return callAccountFacade(() -> accountFacade.resolveAccount(firebaseUid));
    }

    public List<Long> getLocationScope(Long accountId) {
        return callAccountFacade(() -> accountFacade.getLocationScope(accountId));
    }

    public Optional<ActorStatus> getAccountStatus(Long accountId) {
        return callAccountFacade(() -> accountFacade.getAccountStatus(accountId));
    }

    public Optional<TargetAccountSnapshot> getTargetAccount(Long accountId) {
        return callAccountFacade(() -> accountFacade.getTargetAccount(accountId));
    }

    public RosterOutcome findEligibleRoster(Long locationId) {
        return callAccountFacade(() -> accountFacade.findEligibleRoster(locationId));
    }

    public Optional<String> findAccountFullName(Long accountId) {
        return callAccountFacade(() -> accountFacade.findAccountFullName(accountId));
    }

    private <T> T callAccountFacade(Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException ex) {
            log.error("Account facade call failed; failing closed: {}", ex.toString());
            throw new WorkshopDependencyUnavailableException("The account service is temporarily unavailable", ex);
        }
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
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseToken decodedToken;
            try {
                decodedToken = auth.verifyIdToken(token, true);
            } catch (FirebaseAuthException e) {
                String errorCode = e.getAuthErrorCode() != null ? e.getAuthErrorCode().name() : "";
                if ("REVOKED_ID_TOKEN".equalsIgnoreCase(errorCode) || "EXPIRED_ID_TOKEN".equalsIgnoreCase(errorCode)) {
                    throw e;
                }
                log.warn("Firebase checkRevoked failed ({}: {}). Falling back to cryptographic verification.", errorCode, e.getMessage());
                decodedToken = auth.verifyIdToken(token, false);
            } catch (Exception e) {
                log.warn("Firebase checkRevoked encountered exception: {}. Falling back to cryptographic verification.", e.getMessage());
                decodedToken = auth.verifyIdToken(token, false);
            }
            return decodedToken.getUid();
        } catch (Exception e) {
            log.warn("Firebase token verification failed");
            throw new WorkshopUnauthorizedException("Invalid, expired or revoked identity token", e);
        }
    }
}
