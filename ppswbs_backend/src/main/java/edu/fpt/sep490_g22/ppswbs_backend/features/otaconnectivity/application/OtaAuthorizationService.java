package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** Server-side OTA action authorization using only canonical Account facts. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtaAuthorizationService {

    private final AccountFacade accountFacade;

    @Value("${workshop.ota.auth.allow-test-tokens:false}")
    private boolean allowTestTokens;

    public AccountSummary authenticate(String authorization) {
        String uid = verifyToken(extractBearerToken(authorization));
        AccountSummary account = accountFacade.resolveAccount(uid)
                .orElseThrow(() -> new OtaForbiddenException("No active Mland account is available for this identity"));
        if (account.status() != ActorStatus.ACTIVE) {
            throw new OtaForbiddenException("An active Mland account is required");
        }
        if (account.role() == null) {
            throw new OtaForbiddenException("A recognized canonical account role is required");
        }
        return account;
    }

    /** Read safe channel and mapping status; connection credentials are never returned here. */
    public AccountSummary requireChannelView(String authorization) {
        AccountSummary actor = authenticate(authorization);
        if (!isManager(actor) && !isAdmin(actor)) {
            throw new OtaForbiddenException("Access requires an active Manager or Admin");
        }
        return actor;
    }

    /** Create mapping: an active Manager or Admin may configure mapping data globally. */
    public AccountSummary requireMappingAdministration(String authorization) {
        return requireChannelView(authorization);
    }

    public AccountSummary requireManager(String authorization) {
        AccountSummary actor = authenticate(authorization);
        if (!isManager(actor)) {
            throw new OtaForbiddenException("Access requires an active Manager");
        }
        return actor;
    }

    /** Reconciliation work is available to active Managers and Admins globally. */
    public AccountSummary requireManagerOrAdmin(String authorization) {
        AccountSummary actor = authenticate(authorization);
        if (!isManager(actor) && !isAdmin(actor)) {
            throw new OtaForbiddenException("Access requires an active Manager or Admin");
        }
        return actor;
    }

    /** Provider connection/credential changes and booking enablement are Admin-only. */
    public AccountSummary requireAdmin(String authorization) {
        AccountSummary actor = authenticate(authorization);
        if (!isAdmin(actor)) {
            throw new OtaForbiddenException("Access requires an active Admin");
        }
        return actor;
    }

    /** Staff add-on actions are allowed only for the canonical location in scope. */
    public AccountSummary requireStaffLocation(String authorization, Long locationId) {
        AccountSummary actor = authenticate(authorization);
        if (!isStaff(actor)) {
            throw new OtaForbiddenException("Access requires an active Staff account");
        }
        if (locationId == null) {
            throw new OtaLocationScopeException("A canonical location is required for this action");
        }
        List<Long> locations = accountFacade.getLocationScope(actor.accountId());
        if (locations == null || !locations.contains(locationId)) {
            throw new OtaLocationScopeException("Location is outside the Staff member's active location scope");
        }
        return actor;
    }

    public boolean isManager(AccountSummary actor) {
        return actor != null && actor.role() == ActorRole.MANAGER;
    }

    public boolean isStaff(AccountSummary actor) {
        return actor != null && actor.role() == ActorRole.STAFF;
    }

    public boolean isAdmin(AccountSummary actor) {
        return actor != null && actor.role() == ActorRole.ADMIN;
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new OtaUnauthorizedException("A valid Bearer token is required");
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new OtaUnauthorizedException("A valid Bearer token is required");
        }
        return token;
    }

    private String verifyToken(String token) {
        if (token.startsWith("test-uid:")) {
            if (!allowTestTokens) {
                throw new OtaUnauthorizedException("The supplied identity token is invalid");
            }
            return token.substring("test-uid:".length());
        }
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                throw new OtaUnauthorizedException("Identity verification is unavailable");
            }
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token, true);
            return decoded.getUid();
        } catch (OtaUnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("OTA identity verification failed");
            throw new OtaUnauthorizedException("The supplied identity token is invalid");
        }
    }
}
