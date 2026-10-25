package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountProvisioningRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AccountSuspendRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InternalAccountCreateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InternalProfileUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.RoleLocationAssignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountProvisioning;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.RevocationStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final TokenVerificationService tokenVerificationService;
    private final AdminAccountService adminAccountService;
    private final SuspensionService suspensionService;
    private final AccountRepository accountRepository;
    private final AccountProfileRepository profileRepository;
    private final AccountLocationAssignmentRepository locationAssignmentRepository;
    private final InternalAccountProvisioningRepository provisioningRepository;

    @GetMapping
    public ResponseEntity<PagedResponse<AccountSummaryResponse>> listAccounts(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestParam(value = "q", required = false) String q,
        @RequestParam(value = "role", required = false) AccountRole role,
        @RequestParam(value = "status", required = false) AccountStatus status,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        verifyAdmin(authHeader);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Account> accountPage = accountRepository.searchAccounts(q, role, status, pageRequest);

        List<AccountSummaryResponse> data = accountPage.getContent().stream()
            .map(this::toSummaryResponse)
            .toList();

        PagedResponse.PageMeta meta = PagedResponse.PageMeta.builder()
            .page(accountPage.getNumber())
            .size(accountPage.getSize())
            .totalElements(accountPage.getTotalElements())
            .totalPages(accountPage.getTotalPages())
            .build();

        return ResponseEntity.ok(PagedResponse.<AccountSummaryResponse>builder()
            .data(data)
            .meta(meta)
            .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDetailResponse> getAccountDetail(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @PathVariable("id") Long id
    ) {
        verifyAdmin(authHeader);

        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));

        Optional<AccountProfile> profileOpt = profileRepository.findByAccountId(id);
        List<AccountLocationAssignment> locAssignments = locationAssignmentRepository.findByAccountIdAndActiveTrue(id);

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

    @PostMapping("/internal")
    public ResponseEntity<InternalAccountSummaryResponse> createInternalAccount(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @Valid @RequestBody InternalAccountCreateRequest request
    ) {
        Account admin = verifyAdmin(authHeader);
        String correlationId = resolveCorrelationId(correlationHeader);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        Account account = adminAccountService.createInternalAccount(
            admin.getId(),
            idempotencyKey.trim(),
            request.getRole(),
            request.getFullName(),
            request.getPhone(),
            request.getLocationIds(),
            correlationId
        );

        Optional<InternalAccountProvisioning> provOpt = provisioningRepository.findByAccountId(account.getId());

        InternalAccountSummaryResponse resp = InternalAccountSummaryResponse.builder()
            .accountId(account.getId())
            .role(account.getRole())
            .status(account.getStatus())
            .loginEmail(account.getLoginEmail())
            .revocationStatus(toRevocationStatus(account))
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .provisioningStatus(provOpt.map(InternalAccountProvisioning::getStatus).orElse(null))
            .invitationLastSentAt(provOpt.map(InternalAccountProvisioning::getInvitationLastSentAt).orElse(null))
            .build();

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/invitation/resend")
    public ResponseEntity<InternalAccountSummaryResponse> resendInvitation(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id
    ) {
        Account admin = verifyAdmin(authHeader);
        String correlationId = resolveCorrelationId(correlationHeader);

        InternalAccountProvisioning prov = adminAccountService.resendInvitation(admin.getId(), id, correlationId);
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));

        InternalAccountSummaryResponse resp = InternalAccountSummaryResponse.builder()
            .accountId(account.getId())
            .role(account.getRole())
            .status(account.getStatus())
            .loginEmail(account.getLoginEmail())
            .revocationStatus(toRevocationStatus(account))
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .provisioningStatus(prov.getStatus())
            .invitationLastSentAt(prov.getInvitationLastSentAt())
            .build();

        return ResponseEntity.ok(resp);
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<AccountDetailResponse> updateAccountProfileAdmin(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id,
        @Valid @RequestBody InternalProfileUpdateRequest request
    ) {
        Account admin = verifyAdmin(authHeader);
        String correlationId = resolveCorrelationId(correlationHeader);

        adminAccountService.updateInternalProfile(
            admin.getId(),
            id,
            request.getVersion(),
            request.getFullName(),
            request.getPhone(),
            correlationId
        );

        return getAccountDetail(authHeader, id);
    }

    @PutMapping("/{id}/role-location")
    public ResponseEntity<AccountDetailResponse> assignRoleAndLocation(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id,
        @Valid @RequestBody RoleLocationAssignRequest request
    ) {
        Account admin = verifyAdmin(authHeader);
        String correlationId = resolveCorrelationId(correlationHeader);

        adminAccountService.assignRoleAndLocation(
            admin.getId(),
            id,
            request.getVersion(),
            request.getRole(),
            request.getLocationIds(),
            correlationId
        );

        return getAccountDetail(authHeader, id);
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<AccountSummaryResponse> suspendAccount(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id,
        @Valid @RequestBody AccountSuspendRequest request
    ) {
        Account admin = verifyAdmin(authHeader);
        String correlationId = resolveCorrelationId(correlationHeader);

        Account suspended = suspensionService.suspendAccount(admin.getId(), id, request.getReason(), correlationId);

        return ResponseEntity.ok(toSummaryResponse(suspended));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<AccountSummaryResponse> restoreAccount(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id
    ) {
        Account admin = verifyAdmin(authHeader);
        String correlationId = resolveCorrelationId(correlationHeader);

        Account restored = suspensionService.restoreAccount(admin.getId(), id, correlationId);

        return ResponseEntity.ok(toSummaryResponse(restored));
    }

    private Account verifyAdmin(String authHeader) {
        String token = extractBearerToken(authHeader);
        VerifiedIdentity identity = tokenVerificationService.verify(token);

        Account account = accountRepository.findByFirebaseUid(identity.uid())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found for verified identity"));

        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new AccountSuspendedException("Account is SUSPENDED");
        }

        if (account.getRole() != AccountRole.ADMIN) {
            throw new RoleAssignmentForbiddenException("User is not authorized for Admin operations");
        }

        return account;
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
