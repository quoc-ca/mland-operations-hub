package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.ProfileUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.RevocationStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/me")
@RequiredArgsConstructor
public class AccountController {

    private final TokenVerificationService tokenVerificationService;
    private final AccountBootstrapService bootstrapService;
    private final ProfileService profileService;
    private final EmailSyncService emailSyncService;
    private final AccountRepository accountRepository;
    private final AccountProfileRepository profileRepository;
    private final AccountLocationAssignmentRepository locationAssignmentRepository;

    @PutMapping
    public ResponseEntity<AccountSummaryResponse> bootstrapOwnAccount(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader
    ) {
        String token = extractBearerToken(authHeader);
        String correlationId = resolveCorrelationId(correlationHeader);

        VerifiedIdentity identity = tokenVerificationService.verify(token);
        Account account = bootstrapService.bootstrapAccount(identity, correlationId);

        return ResponseEntity.ok(toSummaryResponse(account));
    }

    @GetMapping
    public ResponseEntity<AccountDetailResponse> getOwnAccount(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        String token = extractBearerToken(authHeader);
        VerifiedIdentity identity = tokenVerificationService.verify(token);

        Account account = accountRepository.findByFirebaseUid(identity.uid())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found for verified identity"));

        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new AccountSuspendedException("Account is SUSPENDED");
        }

        Optional<AccountProfile> profileOpt = profileRepository.findByAccountId(account.getId());
        List<AccountLocationAssignment> locAssignments = locationAssignmentRepository.findByAccountIdAndActiveTrue(account.getId());

        AccountDetailResponse detail = AccountDetailResponse.builder()
            .accountId(account.getId())
            .role(account.getRole())
            .status(account.getStatus())
            .loginEmail(account.getLoginEmail())
            .revocationStatus(toRevocationStatus(account))
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .profile(profileOpt.map(p -> AccountProfileResponse.builder()
                .fullName(p.getFullName())
                .phone(p.getPhone())
                .build()).orElse(null))
            .locationAssignments(locAssignments.stream().map(a -> LocationAssignmentResponse.builder()
                .locationId(a.getLocationId())
                .assignedAt(a.getAssignedAt())
                .active(a.isActive())
                .build()).toList())
            .version(account.getVersion())
            .build();

        return ResponseEntity.ok(detail);
    }

    @PutMapping("/profile")
    public ResponseEntity<AccountProfileResponse> updateOwnProfile(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @Valid @RequestBody ProfileUpdateRequest request
    ) {
        String token = extractBearerToken(authHeader);
        String correlationId = resolveCorrelationId(correlationHeader);

        VerifiedIdentity identity = tokenVerificationService.verify(token);
        if (!identity.emailVerified()) {
            throw new EmailNotVerifiedException("Email must be verified to update profile");
        }

        Account account = accountRepository.findByFirebaseUid(identity.uid())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found for verified identity"));

        AccountProfile profile = profileService.updateProfile(account.getId(), request.getFullName(), request.getPhone(), correlationId);

        return ResponseEntity.ok(AccountProfileResponse.builder()
            .fullName(profile.getFullName())
            .phone(profile.getPhone())
            .build());
    }

    @PostMapping("/email-sync")
    public ResponseEntity<Map<String, String>> syncOwnEmail(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader
    ) {
        String token = extractBearerToken(authHeader);
        String correlationId = resolveCorrelationId(correlationHeader);

        VerifiedIdentity identity = tokenVerificationService.verify(token);
        Account account = accountRepository.findByFirebaseUid(identity.uid())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found for verified identity"));

        Account updated = emailSyncService.syncEmail(account.getId(), identity, correlationId);

        return ResponseEntity.ok(Map.of("loginEmail", updated.getLoginEmail() != null ? updated.getLoginEmail() : ""));
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> signOut(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        // Sign-out clears any server-side state (stateless REST)
        return ResponseEntity.noContent().build();
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Missing or malformed Authorization header");
        }
        return authHeader.substring(7).trim();
    }

    private String resolveCorrelationId(String header) {
        return (header != null && !header.isBlank()) ? header.trim() : UUID.randomUUID().toString();
    }

    private AccountSummaryResponse toSummaryResponse(Account account) {
        return AccountSummaryResponse.builder()
            .accountId(account.getId())
            .role(account.getRole())
            .status(account.getStatus())
            .loginEmail(account.getLoginEmail())
            .revocationStatus(toRevocationStatus(account))
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .build();
    }

    private RevocationStatus toRevocationStatus(Account account) {
        if (account.getStatus() != AccountStatus.SUSPENDED) {
            return RevocationStatus.NOT_APPLICABLE;
        }
        return account.getSessionRevocationConfirmedAt() != null
            ? RevocationStatus.CONFIRMED
            : RevocationStatus.PENDING;
    }
}
