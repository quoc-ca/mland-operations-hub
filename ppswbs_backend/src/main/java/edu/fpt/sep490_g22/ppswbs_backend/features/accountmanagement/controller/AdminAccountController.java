package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountProvisioningRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AccountSuspendRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InternalAccountCreateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InternalProfileUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.RoleLocationAssignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.StatusCommandRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountProvisioning;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.RevocationStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;
    private final SuspensionService suspensionService;
    private final AccountRepository accountRepository;
    private final AccountProfileRepository profileRepository;
    private final AccountLocationAssignmentRepository locationAssignmentRepository;
    private final InternalAccountProvisioningRepository provisioningRepository;

    @GetMapping({"/api/v1/accounts", "/api/v1/admin/accounts"})
    public ResponseEntity<PagedResponse<AccountSummaryResponse>> listAccounts(
        @RequestParam(value = "q", required = false) String q,
        @RequestParam(value = "role", required = false) AccountRole role,
        @RequestParam(value = "status", required = false) AccountStatus status,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        verifyAdmin();

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

    @GetMapping({"/api/v1/accounts/{id}", "/api/v1/admin/accounts/{id}"})
    public ResponseEntity<AccountDetailResponse> getAccountDetail(@PathVariable("id") Long id) {
        verifyAdmin();

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

    @PostMapping({"/api/v1/internal-accounts", "/api/v1/admin/accounts/internal"})
    public ResponseEntity<InternalAccountSummaryResponse> createInternalAccount(
        @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @Valid @RequestBody InternalAccountCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        CurrentAccountActor admin = verifyAdmin();
        String correlationId = resolveCorrelationId(correlationHeader, httpRequest);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        Account account = adminAccountService.createInternalAccount(
            admin.accountId(),
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

    @PostMapping({"/api/v1/accounts/{id}/setup-invitations", "/api/v1/admin/accounts/{id}/invitation/resend", "/api/v1/admin/accounts/{id}/invitations"})
    public ResponseEntity<InternalAccountSummaryResponse> resendInvitation(
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id,
        HttpServletRequest httpRequest
    ) {
        CurrentAccountActor admin = verifyAdmin();
        String correlationId = resolveCorrelationId(correlationHeader, httpRequest);

        InternalAccountProvisioning prov = adminAccountService.resendInvitation(admin.accountId(), id, correlationId);
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

    @PutMapping({"/api/v1/accounts/{id}/profile", "/api/v1/admin/accounts/{id}/profile"})
    public ResponseEntity<AccountDetailResponse> updateAccountProfileAdmin(
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id,
        @Valid @RequestBody InternalProfileUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        CurrentAccountActor admin = verifyAdmin();
        String correlationId = resolveCorrelationId(correlationHeader, httpRequest);

        adminAccountService.updateInternalProfile(
            admin.accountId(),
            id,
            request.getVersion(),
            request.getFullName(),
            request.getPhone(),
            correlationId
        );

        return getAccountDetail(id);
    }

    @PutMapping({"/api/v1/accounts/{id}/role-assignment", "/api/v1/admin/accounts/{id}/role-location"})
    public ResponseEntity<AccountDetailResponse> assignRoleAndLocation(
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id,
        @Valid @RequestBody RoleLocationAssignRequest request,
        HttpServletRequest httpRequest
    ) {
        CurrentAccountActor admin = verifyAdmin();
        String correlationId = resolveCorrelationId(correlationHeader, httpRequest);

        adminAccountService.assignRoleAndLocation(
            admin.accountId(),
            id,
            request.getVersion(),
            request.getRole(),
            request.getLocationIds(),
            correlationId
        );

        return getAccountDetail(id);
    }

    @PutMapping("/api/v1/accounts/{id}/status")
    public ResponseEntity<AccountSummaryResponse> updateAccountStatus(
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id,
        @Valid @RequestBody StatusCommandRequest request,
        HttpServletRequest httpRequest
    ) {
        CurrentAccountActor admin = verifyAdmin();
        String correlationId = resolveCorrelationId(correlationHeader, httpRequest);

        Account account;
        if ("SUSPEND".equalsIgnoreCase(request.getOperation())) {
            account = suspensionService.suspendAccount(admin.accountId(), id, request.getReason(), correlationId);
        } else if ("UNSUSPEND".equalsIgnoreCase(request.getOperation())) {
            account = suspensionService.restoreAccount(admin.accountId(), id, correlationId);
        } else {
            throw new IllegalArgumentException("Unknown status command operation: " + request.getOperation());
        }

        return ResponseEntity.ok(toSummaryResponse(account));
    }

    @PostMapping("/api/v1/admin/accounts/{id}/suspend")
    public ResponseEntity<AccountSummaryResponse> suspendAccount(
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id,
        @Valid @RequestBody AccountSuspendRequest request,
        HttpServletRequest httpRequest
    ) {
        CurrentAccountActor admin = verifyAdmin();
        String correlationId = resolveCorrelationId(correlationHeader, httpRequest);

        Account suspended = suspensionService.suspendAccount(admin.accountId(), id, request.getReason(), correlationId);
        return ResponseEntity.ok(toSummaryResponse(suspended));
    }

    @PostMapping("/api/v1/admin/accounts/{id}/restore")
    public ResponseEntity<AccountSummaryResponse> restoreAccount(
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
        @PathVariable("id") Long id,
        HttpServletRequest httpRequest
    ) {
        CurrentAccountActor admin = verifyAdmin();
        String correlationId = resolveCorrelationId(correlationHeader, httpRequest);

        Account restored = suspensionService.restoreAccount(admin.accountId(), id, correlationId);
        return ResponseEntity.ok(toSummaryResponse(restored));
    }

    private CurrentAccountActor verifyAdmin() {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        if (actor.role() != ActorRole.ADMIN) {
            throw new RoleAssignmentForbiddenException("User is not authorized for Admin operations");
        }
        return actor;
    }

    private String resolveCorrelationId(String header, HttpServletRequest request) {
        if (header != null && !header.isBlank()) return header.trim();
        String reqHeader = request.getHeader("X-Correlation-Id");
        if (reqHeader != null && !reqHeader.isBlank()) return reqHeader.trim();
        return UUID.randomUUID().toString();
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
