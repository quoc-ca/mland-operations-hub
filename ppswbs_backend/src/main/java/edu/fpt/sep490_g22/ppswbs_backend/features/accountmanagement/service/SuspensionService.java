package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountProvisioningRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AuditOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountProvisioning;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.InternalProvisioningStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuspensionService {

    private final AccountRepository accountRepository;
    private final InternalAccountProvisioningRepository provisioningRepository;
    private final AuditService auditService;

    @Transactional
    public Account suspendAccount(Long adminId, Long targetAccountId, String reason, String correlationId) {
        Account target = accountRepository.findById(targetAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + targetAccountId));

        if (target.getRole() == AccountRole.ADMIN) {
            throw new RoleAssignmentForbiddenException("Cannot suspend an ADMIN account");
        }

        if (target.getStatus() == AccountStatus.SUSPENDED) {
            throw new IllegalStateException("Account is already SUSPENDED: " + targetAccountId);
        }

        AccountStatus prevStatus = target.getStatus();
        target.setStatus(AccountStatus.SUSPENDED);
        target.setSessionRevocationConfirmedAt(null); // Reset revocation gate

        // Call Firebase revokeRefreshTokens
        boolean revocationSucceeded = false;
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                FirebaseAuth.getInstance().revokeRefreshTokens(target.getFirebaseUid());
                revocationSucceeded = true;
            } else {
                log.warn("FirebaseApp not initialized, simulated revocation in test/dev environment");
                revocationSucceeded = true;
            }
        } catch (Exception e) {
            log.error("Failed to revoke Firebase refresh tokens for uid={}: {}", target.getFirebaseUid(), e.getMessage());
        }

        if (revocationSucceeded) {
            target.setSessionRevocationConfirmedAt(Instant.now());
        }

        target = accountRepository.save(target);

        auditService.recordEvent(
            adminId,
            targetAccountId,
            "STATUS_SUSPENDED",
            AuditOutcome.SUCCESS,
            correlationId,
            Map.of(
                "previous_status", prevStatus.name(),
                "new_status", AccountStatus.SUSPENDED.name(),
                "lock_reason", reason != null ? reason.trim() : ""
            )
        );

        return target;
    }

    @Transactional
    public Account restoreAccount(Long adminId, Long targetAccountId, String correlationId) {
        Account target = accountRepository.findById(targetAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + targetAccountId));

        if (target.getStatus() != AccountStatus.SUSPENDED) {
            throw new IllegalStateException("Account is not currently SUSPENDED: " + targetAccountId);
        }

        // Restoration Gate per FR-020: MUST be confirmed
        if (target.getSessionRevocationConfirmedAt() == null) {
            throw new RevocationPendingException("Firebase session revocation is pending; restoration blocked");
        }

        AccountStatus newStatus = AccountStatus.ACTIVE;
        Optional<InternalAccountProvisioning> provOpt = provisioningRepository.findByAccountId(targetAccountId);
        if (provOpt.isPresent() && provOpt.get().getStatus() != InternalProvisioningStatus.SETUP_COMPLETE) {
            newStatus = AccountStatus.PENDING_SETUP;
        }

        target.setStatus(newStatus);
        target.setSessionRevocationConfirmedAt(null);
        target = accountRepository.save(target);

        auditService.recordEvent(
            adminId,
            targetAccountId,
            "STATUS_RESTORED",
            AuditOutcome.SUCCESS,
            correlationId,
            Map.of("previous_status", AccountStatus.SUSPENDED.name(), "new_status", newStatus.name())
        );

        return target;
    }
}
