package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.ProfileUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.AccountResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.LocationRefResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.ProfileResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/me")
@RequiredArgsConstructor
public class AccountController {

    private final AccountBootstrapService bootstrapService;
    private final ProfileService profileService;
    private final EmailSyncService emailSyncService;
    private final AccountRepository accountRepository;
    private final AccountProfileRepository profileRepository;
    private final AccountLocationAssignmentRepository locationAssignmentRepository;

    @PutMapping
    public ResponseEntity<AccountResponse> bootstrapOwnAccount(
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(correlationHeader, request);
        VerifiedIdentity identity = (VerifiedIdentity) request.getAttribute("verifiedIdentity");
        if (identity == null) {
            throw new InvalidTokenException("No verified identity available in request");
        }

        Account account = bootstrapService.bootstrapAccount(identity, correlationId);
        return ResponseEntity.ok(toAccountResponse(account, identity.emailVerified()));
    }

    @GetMapping
    public ResponseEntity<AccountResponse> getOwnAccount(HttpServletRequest request) {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        VerifiedIdentity identity = (VerifiedIdentity) request.getAttribute("verifiedIdentity");
        boolean emailVerified = identity != null ? identity.emailVerified() : actor.emailVerified();

        Account account = accountRepository.findById(actor.accountId())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + actor.accountId()));

        return ResponseEntity.ok(toAccountResponse(account, emailVerified));
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getOwnProfile(HttpServletRequest request) {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        VerifiedIdentity identity = (VerifiedIdentity) request.getAttribute("verifiedIdentity");
        boolean emailVerified = identity != null ? identity.emailVerified() : actor.emailVerified();

        Account account = accountRepository.findById(actor.accountId())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + actor.accountId()));

        Optional<AccountProfile> profileOpt = profileRepository.findByAccountId(account.getId());
        return ResponseEntity.ok(toProfileResponse(account, profileOpt.orElse(null), emailVerified));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateOwnProfile(
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @Valid @RequestBody ProfileUpdateRequest updateRequest,
        HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(correlationHeader, request);
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        VerifiedIdentity identity = (VerifiedIdentity) request.getAttribute("verifiedIdentity");
        boolean emailVerified = identity != null ? identity.emailVerified() : actor.emailVerified();

        if (!emailVerified) {
            throw new EmailNotVerifiedException("Email must be verified to update profile");
        }

        Account account = accountRepository.findById(actor.accountId())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + actor.accountId()));

        AccountProfile profile = profileService.updateProfile(
            account.getId(),
            updateRequest.getFullName(),
            updateRequest.getPhone(),
            correlationId
        );

        return ResponseEntity.ok(toProfileResponse(account, profile, emailVerified));
    }

    @PutMapping("/email")
    public ResponseEntity<AccountResponse> syncOwnEmail(
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(correlationHeader, request);
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        VerifiedIdentity identity = (VerifiedIdentity) request.getAttribute("verifiedIdentity");
        if (identity == null) {
            throw new InvalidTokenException("No verified identity available in request");
        }

        Account account = emailSyncService.syncEmail(actor.accountId(), identity, correlationId);
        return ResponseEntity.ok(toAccountResponse(account, identity.emailVerified()));
    }

    private AccountResponse toAccountResponse(Account account, boolean emailVerified) {
        List<LocationRefResponse> locations = locationAssignmentRepository.findByAccountIdAndActiveTrue(account.getId()).stream()
            .map(a -> new LocationRefResponse(String.valueOf(a.getLocationId()), "Location " + a.getLocationId()))
            .toList();

        return AccountResponse.builder()
            .id(String.valueOf(account.getId()))
            .email(account.getLoginEmail())
            .emailVerified(emailVerified)
            .role(account.getRole())
            .status(account.getStatus())
            .locations(locations)
            .version(account.getVersion())
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .build();
    }

    private ProfileResponse toProfileResponse(Account account, AccountProfile profile, boolean emailVerified) {
        List<LocationRefResponse> locations = locationAssignmentRepository.findByAccountIdAndActiveTrue(account.getId()).stream()
            .map(a -> new LocationRefResponse(String.valueOf(a.getLocationId()), "Location " + a.getLocationId()))
            .toList();

        return ProfileResponse.builder()
            .id(String.valueOf(account.getId()))
            .email(account.getLoginEmail())
            .emailVerified(emailVerified)
            .role(account.getRole())
            .status(account.getStatus())
            .locations(locations)
            .version(account.getVersion())
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .fullName(profile != null ? profile.getFullName() : null)
            .phone(profile != null ? profile.getPhone() : null)
            .build();
    }

    private String resolveCorrelationId(String header, HttpServletRequest request) {
        if (header != null && !header.isBlank()) return header.trim();
        String reqHeader = request.getHeader("X-Correlation-Id");
        if (reqHeader != null && !reqHeader.isBlank()) return reqHeader.trim();
        return UUID.randomUUID().toString();
    }
}
