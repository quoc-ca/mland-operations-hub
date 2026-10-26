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

    public AccountSummary requireAllBranchActor(String authHeader) {
        AccountSummary account = authenticate(authHeader);

        if (!isAllBranchActor(account)) {
            log.warn("Access denied for accountId={}: expected all-branch/higher-privilege actor, found role={}",
                    account.accountId(), account.role());
            throw new WorkshopForbiddenException("Access requires all-branch higher-privilege authority");
        }

        return account;
    }

    public boolean isAllBranchActor(AccountSummary account) {
        return account != null && account.role() != null && "ADMIN".equalsIgnoreCase(account.role().name());
    }

    public boolean isManager(AccountSummary account) {
        return account != null && account.role() != null && "MANAGER".equalsIgnoreCase(account.role().name());
    }

    public boolean isStaff(AccountSummary account) {
        return account != null && account.role() != null && "STAFF".equalsIgnoreCase(account.role().name());
    }

    public AccountSummary requireManagerBranchScope(String authHeader, Long branchId) {
        AccountSummary account = authenticate(authHeader);

        if (!isManager(account)) {
            log.warn("Access denied for accountId={}: expected MANAGER, found {}", account.accountId(), account.role());
            throw new WorkshopForbiddenException("Access requires MANAGER role");
        }

        List<Long> locationScope = accountFacade.getLocationScope(account.accountId());
        if (!locationScope.contains(branchId)) {
            log.warn("Branch scope check failed for accountId={}, branchId={}, allowedScope={}",
                account.accountId(), branchId, locationScope);
            throw new WorkshopBranchScopeException("Branch ID " + branchId + " is outside actor's active location scope");
        }

        return account;
    }

    public AccountSummary requireStaffSessionAssignment(String authHeader, Long sessionId) {
        AccountSummary account = authenticate(authHeader);

        WorkshopSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new WorkshopNotFoundException("Workshop session not found with ID " + sessionId));

        Long branchId = session.getBranch().getId();

        if (isStaff(account)) {
            boolean assigned = staffAssignmentRepository.existsBySessionIdAndPersonnelIdAndActiveTrue(sessionId, account.accountId());
            if (!assigned) {
                log.warn("Staff assignment check failed for accountId={}, sessionId={}", account.accountId(), sessionId);
                throw new WorkshopForbiddenException("Staff member is not assigned to session ID " + sessionId);
            }

            List<Long> locationScope = accountFacade.getLocationScope(account.accountId());
            if (!locationScope.contains(branchId)) {
                log.warn("Staff location scope check failed for accountId={}, branchId={}, allowedScope={}",
                    account.accountId(), branchId, locationScope);
                throw new WorkshopBranchScopeException("Branch ID " + branchId + " is outside Staff member's active location scope");
            }

            return account;
        }

        if (isManager(account)) {
            boolean isLead = operationalLeadRepository.existsBySessionIdAndManagerPersonnelIdAndActiveTrue(sessionId, account.accountId());
            if (!isLead) {
                log.warn("Manager lead check failed for accountId={}, sessionId={}", account.accountId(), sessionId);
                throw new WorkshopForbiddenException("Manager is not the recorded operational lead for session ID " + sessionId);
            }

            List<Long> locationScope = accountFacade.getLocationScope(account.accountId());
            if (!locationScope.contains(branchId)) {
                log.warn("Manager operational lead branch scope check failed for accountId={}, branchId={}", account.accountId(), branchId);
                throw new WorkshopBranchScopeException("Branch ID " + branchId + " is outside Manager location scope");
            }

            return account;
        }

        log.warn("Access denied for accountId={}, role={}: neither assigned Staff nor Manager operational lead",
            account.accountId(), account.role());
        throw new WorkshopForbiddenException("Access denied: Actor is not assigned Staff or operational lead");
    }

    public AccountSummary requireManagerOrAssignedStaff(String authHeader, Long sessionId, Long branchId) {
        AccountSummary account = authenticate(authHeader);

        List<Long> locationScope = accountFacade.getLocationScope(account.accountId());
        if (!locationScope.contains(branchId)) {
            throw new WorkshopBranchScopeException("Branch ID " + branchId + " is outside actor location scope");
        }

        if (isManager(account)) {
            return account;
        }

        if (isStaff(account)) {
            boolean assigned = staffAssignmentRepository.existsBySessionIdAndPersonnelIdAndActiveTrue(sessionId, account.accountId());
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
