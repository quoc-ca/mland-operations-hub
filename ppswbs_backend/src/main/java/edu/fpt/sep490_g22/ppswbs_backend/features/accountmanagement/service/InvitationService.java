package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountInvitationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.InvitationStatusResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountInvitation;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
@Slf4j
public class InvitationService implements InvitationDeliveryFacade {

    private static final Duration INVITATION_LIFETIME = Duration.ofHours(48);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(5);
    private static final String DEFAULT_KEY_VERSION = "v1";

    private final InternalAccountInvitationRepository invitationRepository;
    private final AccountRepository accountRepository;
    private final AccountProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final String signingKey;
    private final String frontendBaseUrl;

    public InvitationService(
            InternalAccountInvitationRepository invitationRepository,
            AccountRepository accountRepository,
            AccountProfileRepository profileRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.account.invitation.signing-key:mland-internal-account-invitation-signing-key-default-v1-32chars}") String signingKey,
            @Value("${app.frontend.url:http://localhost:3000}") String frontendBaseUrl) {
        this.invitationRepository = invitationRepository;
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.eventPublisher = eventPublisher;
        this.signingKey = signingKey;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
    }

    @Transactional
    public InternalAccountInvitation issueInitialInvitation(Account account, Long adminId, String correlationId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(INVITATION_LIFETIME);

        // Pre-generate ID or use deterministic surrogate parameters
        int version = 1;
        String rawSecret = deriveDeterministicSecret(account.getId(), version, DEFAULT_KEY_VERSION);
        String secretDigest = sha256Hex(rawSecret);

        InternalAccountInvitation invitation = InternalAccountInvitation.builder()
                .accountId(account.getId())
                .invitationVersion(version)
                .secretDigest(secretDigest)
                .keyVersion(DEFAULT_KEY_VERSION)
                .createdAt(now)
                .expiresAt(expiresAt)
                .useState("PENDING")
                .deliveryState("QUEUED")
                .createdByAdminId(adminId)
                .build();

        invitation = invitationRepository.save(invitation);

        publishInvitationEvent(invitation, account, correlationId);
        return invitation;
    }

    @Transactional
    public InternalAccountInvitation rotateInvitation(Account account, Long adminId, String correlationId) {
        Optional<InternalAccountInvitation> latestOpt = invitationRepository.findTopByAccountIdOrderByInvitationVersionDesc(account.getId());
        Instant now = Instant.now();

        if (latestOpt.isPresent()) {
            InternalAccountInvitation latest = latestOpt.get();
            if (now.isBefore(latest.getCreatedAt().plus(RESEND_COOLDOWN))) {
                throw new InvitationCooldownActiveException("Vui lòng đợi ít nhất 5 phút giữa các lần gửi lại thư mời.");
            }
            // Invalidate prior active version
            if ("PENDING".equalsIgnoreCase(latest.getUseState())) {
                latest.setUseState("INVALIDATED");
                invitationRepository.save(latest);
            }
        }

        int nextVersion = latestOpt.map(i -> i.getInvitationVersion() + 1).orElse(1);
        Instant expiresAt = now.plus(INVITATION_LIFETIME);
        String rawSecret = deriveDeterministicSecret(account.getId(), nextVersion, DEFAULT_KEY_VERSION);
        String secretDigest = sha256Hex(rawSecret);

        InternalAccountInvitation newInvitation = InternalAccountInvitation.builder()
                .accountId(account.getId())
                .invitationVersion(nextVersion)
                .secretDigest(secretDigest)
                .keyVersion(DEFAULT_KEY_VERSION)
                .createdAt(now)
                .expiresAt(expiresAt)
                .useState("PENDING")
                .deliveryState("QUEUED")
                .createdByAdminId(adminId)
                .build();

        newInvitation = invitationRepository.save(newInvitation);

        publishInvitationEvent(newInvitation, account, correlationId);
        return newInvitation;
    }

    @Override
    @Transactional(readOnly = true)
    public MaterializationOutcome materialize(MaterializationRequest request) {
        if (request == null || request.getInvitationId() == null) {
            return MaterializationOutcome.unavailable("Invalid materialization request");
        }

        Optional<InternalAccountInvitation> invOpt = invitationRepository.findById(request.getInvitationId());
        if (invOpt.isEmpty()) {
            return MaterializationOutcome.unavailable("Invitation not found");
        }

        InternalAccountInvitation invitation = invOpt.get();

        // Check version mismatch
        if (invitation.getInvitationVersion() != request.getVersion()) {
            return MaterializationOutcome.stale("Invitation version " + request.getVersion() + " has been superseded by version " + invitation.getInvitationVersion());
        }

        // Check use state
        if (!"PENDING".equalsIgnoreCase(invitation.getUseState())) {
            return MaterializationOutcome.stale("Invitation is no longer pending (current state: " + invitation.getUseState() + ")");
        }

        // Check expiration
        Instant now = Instant.now();
        if (now.isAfter(invitation.getExpiresAt())) {
            return MaterializationOutcome.stale("Invitation expired at " + invitation.getExpiresAt());
        }

        // If manual content, verify ADMIN actor
        if (request.getPurpose() == MaterializationPurpose.MANUAL_CONTENT) {
            if (request.getAdminActorAccountId() == null) {
                return MaterializationOutcome.unavailable("Admin actor ID required for manual content retrieval");
            }
            Account adminAccount = accountRepository.findById(request.getAdminActorAccountId()).orElse(null);
            if (adminAccount == null || adminAccount.getRole() != AccountRole.ADMIN || adminAccount.getStatus() != AccountStatus.ACTIVE) {
                return MaterializationOutcome.unavailable("Actor is not an active Admin");
            }
        }

        // Reproduce transient URL deterministically
        String rawSecret = deriveDeterministicSecret(invitation.getAccountId(), invitation.getInvitationVersion(), invitation.getKeyVersion());
        String transientUrl = frontendBaseUrl + "/setup-account?secret=" + rawSecret;

        return MaterializationOutcome.available(transientUrl, invitation.getExpiresAt());
    }

    @Override
    @Transactional
    public void reportDeliveryState(InvitationDeliveryReport report) {
        if (report == null || report.invitationId() == null) {
            return;
        }

        Optional<InternalAccountInvitation> invOpt = invitationRepository.findById(report.invitationId());
        if (invOpt.isEmpty()) {
            log.warn("Delivery report for unknown invitation {}", report.invitationId());
            return;
        }

        InternalAccountInvitation invitation = invOpt.get();
        if (invitation.getInvitationVersion() == report.version()) {
            invitation.setDeliveryState(report.deliveryState());
            if (report.notificationRequestId() != null) {
                invitation.setNotificationRequestId(report.notificationRequestId());
            }
            invitationRepository.save(invitation);
            log.info("Updated invitation {} v{} deliveryState to {}", invitation.getId(), invitation.getInvitationVersion(), report.deliveryState());
        } else {
            log.info("Received delivery report for stale invitation version {} (current is {})", report.version(), invitation.getInvitationVersion());
        }
    }

    @Transactional(readOnly = true)
    public InvitationStatusResponse getCurrentStatus(Long accountId) {
        InternalAccountInvitation latest = invitationRepository.findTopByAccountIdOrderByInvitationVersionDesc(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No invitation found for account " + accountId));

        Instant now = Instant.now();
        boolean manualEligible = "FAILED".equalsIgnoreCase(latest.getDeliveryState()) &&
                "PENDING".equalsIgnoreCase(latest.getUseState()) &&
                now.isBefore(latest.getExpiresAt());

        return InvitationStatusResponse.builder()
                .accountId(accountId)
                .invitationVersion(latest.getInvitationVersion())
                .createdAt(latest.getCreatedAt())
                .expiresAt(latest.getExpiresAt())
                .serverNow(now)
                .deliveryState(latest.getDeliveryState())
                .manualContentEligible(manualEligible)
                .notificationRequestId(latest.getNotificationRequestId())
                .build();
    }

    public String deriveDeterministicSecret(Long accountId, int version, String keyVersion) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            String payload = "invitation:" + accountId + ":v" + version + ":k" + keyVersion;
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive deterministic secret", e);
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private void publishInvitationEvent(InternalAccountInvitation invitation, Account account, String correlationId) {
        String fullName = profileRepository.findByAccountId(account.getId())
                .map(AccountProfile::getFullName)
                .orElse("Nhân viên Mland");

        String eventRef = "INVITATION-" + invitation.getId() + "-v" + invitation.getInvitationVersion();

        InternalAccountInvitationRequested event = new InternalAccountInvitationRequested(
                invitation.getId(),
                invitation.getInvitationVersion(),
                account.getId(),
                account.getLoginEmail(),
                fullName,
                eventRef,
                correlationId != null ? correlationId : "inv-corr-" + invitation.getId(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt()
        );

        eventPublisher.publishEvent(event);
        log.info("Published InternalAccountInvitationRequested for account {} version {}", account.getId(), invitation.getInvitationVersion());
    }
}
