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

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;

import java.time.Instant;
import java.util.List;

/**
 * T004 & T011: Server-side authorization service for Workshop Booking.
 * <p>
 * Invariants:
 * - Consumes ONLY public Account Management facade (AccountFacade).
 * - Uses canonical role (MEMBER, STAFF, MANAGER, ADMIN), active status, and activeLocationIds.
 * - Compares support-action location directly against canonical workshop_location.id (Long).
 * - Does NOT use display roles, Firebase claims, or retired role codes.
 * - Does not invent role mappings or edit FE-001 Account Management.
 * - Staff and ADMIN alone do not have Magic-Link incident authority (FR-017b5).
 * - Records minimal Booking business audit evidence on audit-eligible commands and denied attempts.
 * - Denied commands have no business side effects.
 * - Strictly excludes contact proof, raw Magic Link, QR, token, callback payload, and secret from audit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopBookingAuthorizationService {

    private final AccountFacade accountFacade;
    private final BookingAuditEvidenceRepository auditEvidenceRepository;

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

    /**
     * Record minimal business audit evidence. Strictly excludes contact proof, raw tokens, or secrets.
     */
    public BookingAuditEvidence recordAudit(String bookingCode,
                                            Long locationId,
                                            String actorType,
                                            Long actorId,
                                            String action,
                                            String outcome,
                                            String correlationReference,
                                            String managerReason) {
        BookingAuditEvidence evidence = BookingAuditEvidence.builder()
                .bookingCode(bookingCode)
                .locationId(locationId != null ? locationId : 0L)
                .actorType(actorType != null ? actorType : "SYSTEM")
                .actorId(actorId)
                .action(action)
                .outcome(outcome)
                .correlationReference(correlationReference != null ? correlationReference : "UNKNOWN")
                .managerReason(managerReason)
                .occurredAt(Instant.now())
                .build();
        return auditEvidenceRepository.save(evidence);
    }

    /**
     * Record denied attempt audit evidence without side effect.
     */
    public void recordDeniedAttempt(String bookingCode,
                                    Long locationId,
                                    String actorType,
                                    Long actorId,
                                    String action,
                                    String correlationReference,
                                    String reason) {
        try {
            recordAudit(bookingCode, locationId, actorType, actorId, action, "DENIED", correlationReference, reason);
        } catch (Exception e) {
            log.warn("Failed to persist denied audit evidence for bookingCode={}: {}", bookingCode, e.getMessage());
        }
    }

    public AccountSummary requireConfirmationResendAuthority(String authHeader, Long bookingLocationId) {
        return requireConfirmationResendAuthority(authHeader, bookingLocationId, null, null);
    }

    public AccountSummary requireConfirmationResendAuthority(String authHeader,
                                                             Long bookingLocationId,
                                                             String bookingCode,
                                                             String correlationReference) {
        AccountSummary account;
        try {
            account = authenticate(authHeader);
        } catch (Exception ex) {
            if (bookingCode != null) {
                recordDeniedAttempt(bookingCode, bookingLocationId, "UNKNOWN", null, "CONFIRMATION_RESEND", correlationReference, ex.getMessage());
            }
            throw ex;
        }

        if (!isStaff(account) && !isManager(account)) {
            log.warn("Access denied for accountId={}: confirmation resend requires STAFF or MANAGER role, found {}",
                    account.accountId(), account.role());
            if (bookingCode != null) {
                recordDeniedAttempt(bookingCode, bookingLocationId, account.role().name(), account.accountId(),
                        "CONFIRMATION_RESEND", correlationReference, "Role " + account.role() + " not authorized");
            }
            throw new WorkshopBookingForbiddenException("Confirmation resend requires STAFF or MANAGER role");
        }

        if (isStaff(account)) {
            try {
                verifyLocationScope(account.accountId(), bookingLocationId);
            } catch (WorkshopBookingLocationScopeException ex) {
                if (bookingCode != null) {
                    recordDeniedAttempt(bookingCode, bookingLocationId, account.role().name(), account.accountId(),
                            "CONFIRMATION_RESEND", correlationReference, ex.getMessage());
                }
                throw ex;
            }
        }
        return account;
    }

    public AccountSummary requireMagicLinkIncidentAuthority(String authHeader, Long bookingLocationId) {
        return requireMagicLinkIncidentAuthority(authHeader, bookingLocationId, null, "REVOKE", null, null);
    }

    public AccountSummary requireMagicLinkIncidentAuthority(String authHeader,
                                                            Long bookingLocationId,
                                                            String bookingCode,
                                                            String action,
                                                            String reason,
                                                            String correlationReference) {
        AccountSummary account;
        try {
            account = authenticate(authHeader);
        } catch (Exception ex) {
            if (bookingCode != null) {
                recordDeniedAttempt(bookingCode, bookingLocationId, "UNKNOWN", null,
                        "MAGIC_LINK_" + action, correlationReference, ex.getMessage());
            }
            throw ex;
        }

        if (!isManager(account)) {
            log.warn("Access denied for accountId={}, role={}: Magic Link incident handling strictly requires MANAGER role; Staff and ADMIN alone are denied",
                    account.accountId(), account.role());
            if (bookingCode != null) {
                recordDeniedAttempt(bookingCode, bookingLocationId, account.role().name(), account.accountId(),
                        "MAGIC_LINK_" + action, correlationReference, "Strictly requires MANAGER role, found " + account.role());
            }
            throw new WorkshopBookingForbiddenException(
                    "Magic Link incident management strictly requires MANAGER role; Staff and ADMIN alone are denied");
        }

        return account;
    }

    public AccountSummary requireLatePaymentFollowupAuthority(String authHeader, Long bookingLocationId) {
        return requireLatePaymentFollowupAuthority(authHeader, bookingLocationId, null, "RECORD", null, null);
    }

    public AccountSummary requireLatePaymentFollowupAuthority(String authHeader,
                                                              Long bookingLocationId,
                                                              String bookingCode,
                                                              String action,
                                                              String reason,
                                                              String correlationReference) {
        AccountSummary account;
        try {
            account = authenticate(authHeader);
        } catch (Exception ex) {
            if (bookingCode != null) {
                recordDeniedAttempt(bookingCode, bookingLocationId, "UNKNOWN", null,
                        "LATE_PAYMENT_" + action, correlationReference, ex.getMessage());
            }
            throw ex;
        }

        if (!isManager(account)) {
            log.warn("Access denied for accountId={}, role={}: late-payment follow-up requires MANAGER role",
                    account.accountId(), account.role());
            if (bookingCode != null) {
                recordDeniedAttempt(bookingCode, bookingLocationId, account.role().name(), account.accountId(),
                        "LATE_PAYMENT_" + action, correlationReference, "Requires MANAGER role, found " + account.role());
            }
            throw new WorkshopBookingForbiddenException("Late-payment follow-up requires MANAGER role");
        }

        return account;
    }

    public AccountSummary requireMemberOwnership(String authHeader, Long bookingOwnerAccountId) {
        return requireMemberOwnership(authHeader, bookingOwnerAccountId, null, null, null);
    }

    public AccountSummary requireMemberOwnership(String authHeader,
                                                 Long bookingOwnerAccountId,
                                                 String bookingCode,
                                                 Long bookingLocationId,
                                                 String correlationReference) {
        AccountSummary account;
        try {
            account = authenticate(authHeader);
        } catch (Exception ex) {
            if (bookingCode != null) {
                recordDeniedAttempt(bookingCode, bookingLocationId, "UNKNOWN", null,
                        "MEMBER_LOOKUP", correlationReference, ex.getMessage());
            }
            throw ex;
        }

        if (!isMember(account)) {
            log.warn("Access denied for accountId={}, role={}: access requires MEMBER role",
                    account.accountId(), account.role());
            if (bookingCode != null) {
                recordDeniedAttempt(bookingCode, bookingLocationId, account.role().name(), account.accountId(),
                        "MEMBER_LOOKUP", correlationReference, "Requires MEMBER role, found " + account.role());
            }
            throw new WorkshopBookingForbiddenException("Access requires MEMBER role");
        }

        if (bookingOwnerAccountId != null && !account.accountId().equals(bookingOwnerAccountId)) {
            log.warn("Ownership check failed: accountId={} does not match bookingOwnerAccountId={}",
                    account.accountId(), bookingOwnerAccountId);
            if (bookingCode != null) {
                recordDeniedAttempt(bookingCode, bookingLocationId, account.role().name(), account.accountId(),
                        "MEMBER_LOOKUP", correlationReference, "Does not own booking");
            }
            throw new WorkshopBookingForbiddenException(
                    "Account ID " + account.accountId() + " does not own booking belonging to account ID " + bookingOwnerAccountId);
        }

        return account;
    }

    public boolean canResendConfirmation(AccountSummary account, Long bookingLocationId) {
        return account != null
                && account.status() == ActorStatus.ACTIVE
                && (isManager(account) || (isStaff(account) && hasLocationScope(account.accountId(), bookingLocationId)));
    }

    public boolean canHandleMagicLinkIncident(AccountSummary account, Long bookingLocationId) {
        return account != null
                && account.status() == ActorStatus.ACTIVE
                && isManager(account);
    }

    public boolean canFollowUpLatePayment(AccountSummary account, Long bookingLocationId) {
        return account != null
                && account.status() == ActorStatus.ACTIVE
                && isManager(account);
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
