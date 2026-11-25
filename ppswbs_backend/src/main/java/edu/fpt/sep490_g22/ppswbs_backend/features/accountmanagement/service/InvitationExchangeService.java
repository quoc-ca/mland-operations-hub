package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountInvitationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InvitationCompletionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.InvitationCompletionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.InvitationExchangeResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AuditOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountInvitation;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationExchangeService {

    private final InternalAccountInvitationRepository invitationRepository;
    private final AccountRepository accountRepository;
    private final AccountLocationAssignmentRepository locationAssignmentRepository;
    private final AuditService auditService;

    // Ephemeral setup sessions cached in memory with a short TTL (15 minutes)
    private final Map<String, SetupSessionData> sessionCache = new ConcurrentHashMap<>();

    private record SetupSessionData(
            Long accountId,
            Long invitationId,
            String boundFirebaseUid,
            String intendedEmail,
            AccountRole role,
            Instant sessionExpiresAt
    ) {}

    @Transactional(readOnly = true)
    public InvitationExchangeResponse exchangeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Secret is required");
        }

        String digest = InvitationService.sha256Hex(secret.trim());
        InternalAccountInvitation invitation = invitationRepository.findBySecretDigest(digest)
                .orElseThrow(() -> new IllegalArgumentException("Invalid, expired or used invitation"));

        if (!"PENDING".equalsIgnoreCase(invitation.getUseState())) {
            throw new IllegalArgumentException("Invitation is no longer pending (already used or invalidated)");
        }

        Instant now = Instant.now();
        if (now.isAfter(invitation.getExpiresAt())) {
            throw new IllegalArgumentException("Invitation has expired");
        }

        Account account = accountRepository.findById(invitation.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for invitation"));

        if (account.getStatus() != AccountStatus.PENDING_SETUP) {
            throw new IllegalArgumentException("Account is not in pending setup state");
        }

        // Generate Firebase custom token for the bound UID
        String customToken;
        if (!FirebaseApp.getApps().isEmpty()) {
            try {
                customToken = FirebaseAuth.getInstance().createCustomToken(
                        account.getFirebaseUid(),
                        Map.of("role", account.getRole().name())
                );
            } catch (Exception e) {
                log.warn("Firebase custom token generation failed, falling back to simulated token: {}", e.getMessage());
                customToken = "custom-token-sim-" + UUID.randomUUID();
            }
        } else {
            customToken = "custom-token-dev-" + UUID.randomUUID();
        }

        // Create ephemeral setup session (15m validity)
        String setupSession = UUID.randomUUID().toString();
        sessionCache.put(setupSession, new SetupSessionData(
                account.getId(),
                invitation.getId(),
                account.getFirebaseUid(),
                account.getLoginEmail(),
                account.getRole(),
                now.plus(Duration.ofMinutes(15))
        ));

        return InvitationExchangeResponse.builder()
                .firebaseCustomToken(customToken)
                .setupSession(setupSession)
                .expiresAt(invitation.getExpiresAt())
                .build();
    }

    @Transactional
    public InvitationCompletionResponse completeSetup(
            InvitationCompletionRequest request,
            CurrentAccountActor authenticatedActor,
            String correlationId
    ) {
        if (request == null || request.getSetupSession() == null) {
            throw new IllegalArgumentException("Setup session is required");
        }

        SetupSessionData session = sessionCache.get(request.getSetupSession());
        if (session == null || Instant.now().isAfter(session.sessionExpiresAt())) {
            sessionCache.remove(request.getSetupSession());
            throw new OptimisticLockConflictException("Setup session expired or invalid. Please re-exchange your invitation.");
        }

        Account account = accountRepository.findById(session.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (account.getStatus() != AccountStatus.PENDING_SETUP) {
            sessionCache.remove(request.getSetupSession());
            throw new OptimisticLockConflictException("Account is already active or suspended");
        }

        InternalAccountInvitation invitation = invitationRepository.findById(session.invitationId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (!"PENDING".equalsIgnoreCase(invitation.getUseState())) {
            sessionCache.remove(request.getSetupSession());
            throw new OptimisticLockConflictException("Invitation link has already been consumed");
        }

        // Validate that caller's authenticated actor matches bound account
        if (authenticatedActor != null && authenticatedActor.accountId() != null) {
            if (!Objects.equals(authenticatedActor.accountId(), account.getId())) {
                throw new RoleAssignmentForbiddenException("Authenticated actor does not match the invited account");
            }
        }

        // Validate active location scope
        List<AccountLocationAssignment> activeLocations = locationAssignmentRepository.findByAccountIdAndActiveTrue(account.getId());
        if (account.getRole() == AccountRole.STAFF && activeLocations.isEmpty()) {
            throw new LocationValidationException("Active workshop location scope required to complete staff setup");
        } else if (account.getRole() == AccountRole.MANAGER && activeLocations.isEmpty()) {
            throw new LocationValidationException("At least one active workshop location assignment required for manager setup");
        }

        // Single-use atomic update
        Instant now = Instant.now();
        invitation.setUseState("USED");
        invitation.setUsedAt(now);
        invitationRepository.save(invitation);

        account.setStatus(AccountStatus.ACTIVE);
        account.setEmailVerifiedAt(now);
        accountRepository.save(account);

        sessionCache.remove(request.getSetupSession());

        auditService.recordEvent(
                account.getId(),
                account.getId(),
                "ACCOUNT_SETUP_COMPLETED",
                AuditOutcome.SUCCESS,
                correlationId != null ? correlationId : "setup-corr-" + account.getId(),
                Map.of("role", account.getRole().name(), "method", request.getMethod() != null ? request.getMethod() : "UNKNOWN")
        );

        String homeRoute = account.getRole() == AccountRole.STAFF ? "/staff" : "/manager";
        return InvitationCompletionResponse.builder()
                .accountId(account.getId())
                .role(account.getRole().name())
                .status("ACTIVE")
                .homeRoute(homeRoute)
                .build();
    }
}
