package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * T004: Server-side authorization service for Workshop Booking.
 * <p>
 * Invariants:
 * - Consumes ONLY public Account Management facade (AccountFacade).
 * - Uses canonical role (MEMBER, STAFF, MANAGER, ADMIN), active status, and activeLocationIds.
 * - Compares support-action location directly against canonical workshop_location.id (Long).
 * - Does NOT use display roles, Firebase claims, or retired role codes.
 * - Does not invent role mappings or edit FE-001 Account Management.
 * - Staff and ADMIN alone do not have Magic-Link incident authority (FR-017b5).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopBookingAuthorizationService {

    private final AccountFacade accountFacade;

    @Value("${workshop.booking.auth.allow-test-tokens:false}")
    private boolean allowTestTokens;

    public AccountSummary authenticate(String authHeader) {
        String token = extractBearerToken(authHeader);
        String firebaseUid = verifyTokenAndGetUid(token);

        AccountSummary account = accountFacade.resolveAccount(firebaseUid)
                .orElseThrow(() -> new WorkshopBookingForbiddenException("No registered Mland account found for verified identity"));

        if (account.status() == null || account.status() != ActorStatus.ACTIVE) {
            log.warn("Access denied for accountId={}: account status is {}", account.accountId(), account.status());
            throw new WorkshopBookingForbiddenException("Account status is " + account.status() + ", ACTIVE status required");
        }

        return account;
    }

    public AccountSummary requireConfirmationResendAuthority(String authHeader, Long bookingLocationId) {
        AccountSummary account = authenticate(authHeader);

        if (!isStaff(account) && !isManager(account)) {
            log.warn("Access denied for accountId={}: confirmation resend requires STAFF or MANAGER role, found {}",
                    account.accountId(), account.role());
            throw new WorkshopBookingForbiddenException("Confirmation resend requires STAFF or MANAGER role");
        }

        verifyLocationScope(account.accountId(), bookingLocationId);
        return account;
    }

    public AccountSummary requireMagicLinkIncidentAuthority(String authHeader, Long bookingLocationId) {
        AccountSummary account = authenticate(authHeader);

        if (!isManager(account)) {
            log.warn("Access denied for accountId={}, role={}: Magic Link incident handling strictly requires MANAGER role; Staff and ADMIN alone are denied",
                    account.accountId(), account.role());
            throw new WorkshopBookingForbiddenException(
                    "Magic Link incident management strictly requires MANAGER role; Staff and ADMIN alone are denied");
        }

        verifyLocationScope(account.accountId(), bookingLocationId);
        return account;
    }

    public AccountSummary requireLatePaymentFollowupAuthority(String authHeader, Long bookingLocationId) {
        AccountSummary account = authenticate(authHeader);

        if (!isManager(account)) {
            log.warn("Access denied for accountId={}, role={}: late-payment follow-up requires MANAGER role",
                    account.accountId(), account.role());
            throw new WorkshopBookingForbiddenException("Late-payment follow-up requires MANAGER role");
        }

        verifyLocationScope(account.accountId(), bookingLocationId);
        return account;
    }

    public AccountSummary requireMemberOwnership(String authHeader, Long bookingOwnerAccountId) {
        AccountSummary account = authenticate(authHeader);

        if (!isMember(account)) {
            log.warn("Access denied for accountId={}, role={}: access requires MEMBER role",
                    account.accountId(), account.role());
            throw new WorkshopBookingForbiddenException("Access requires MEMBER role");
        }

        if (bookingOwnerAccountId != null && !account.accountId().equals(bookingOwnerAccountId)) {
            log.warn("Ownership check failed: accountId={} does not match bookingOwnerAccountId={}",
                    account.accountId(), bookingOwnerAccountId);
            throw new WorkshopBookingForbiddenException(
                    "Account ID " + account.accountId() + " does not own booking belonging to account ID " + bookingOwnerAccountId);
        }

        return account;
    }

    public boolean canResendConfirmation(AccountSummary account, Long bookingLocationId) {
        return account != null
                && account.status() == ActorStatus.ACTIVE
                && (isStaff(account) || isManager(account))
                && hasLocationScope(account.accountId(), bookingLocationId);
    }

    public boolean canHandleMagicLinkIncident(AccountSummary account, Long bookingLocationId) {
        return account != null
                && account.status() == ActorStatus.ACTIVE
                && isManager(account)
                && hasLocationScope(account.accountId(), bookingLocationId);
    }

    public boolean canFollowUpLatePayment(AccountSummary account, Long bookingLocationId) {
        return account != null
                && account.status() == ActorStatus.ACTIVE
                && isManager(account)
                && hasLocationScope(account.accountId(), bookingLocationId);
    }

    public boolean isOwner(AccountSummary account, Long bookingOwnerAccountId) {
        return account != null
                && account.status() == ActorStatus.ACTIVE
                && isMember(account)
                && account.accountId().equals(bookingOwnerAccountId);
    }

    public boolean isManager(AccountSummary account) {
        return account != null && account.role() == ActorRole.MANAGER;
    }

    public boolean isStaff(AccountSummary account) {
        return account != null && account.role() == ActorRole.STAFF;
    }

    public boolean isMember(AccountSummary account) {
        return account != null && account.role() == ActorRole.MEMBER;
    }

    public boolean isAdmin(AccountSummary account) {
        return account != null && account.role() == ActorRole.ADMIN;
    }

    private void verifyLocationScope(Long accountId, Long bookingLocationId) {
        if (!hasLocationScope(accountId, bookingLocationId)) {
            log.warn("Location scope check failed for accountId={}, locationId={}", accountId, bookingLocationId);
            throw new WorkshopBookingLocationScopeException(
                    "Location ID " + bookingLocationId + " is outside actor's active location scope");
        }
    }

    private boolean hasLocationScope(Long accountId, Long bookingLocationId) {
        if (bookingLocationId == null) {
            return false;
        }
        List<Long> locationScope = accountFacade.getLocationScope(accountId);
        return locationScope != null && locationScope.contains(bookingLocationId);
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new WorkshopBookingUnauthorizedException("Missing or malformed Authorization Bearer header");
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new WorkshopBookingUnauthorizedException("Bearer token is empty");
        }
        return token;
    }

    private String verifyTokenAndGetUid(String token) {
        if (token.startsWith("test-uid:")) {
            if (!allowTestTokens) {
                log.warn("Rejecting test-uid token: test tokens are disabled in current environment");
                throw new WorkshopBookingUnauthorizedException("Test tokens are disabled in this environment");
            }
            return token.substring("test-uid:".length());
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                throw new WorkshopBookingUnauthorizedException("FirebaseApp is not configured");
            }
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token, true);
            return decodedToken.getUid();
        } catch (Exception e) {
            log.warn("Firebase token verification failed");
            throw new WorkshopBookingUnauthorizedException("Invalid, expired or revoked identity token", e);
        }
    }
}
