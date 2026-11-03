package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountProvisioningRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AuditOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountProvisioning;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.InternalProvisioningStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.VerifiedIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountBootstrapService {

    private final AccountRepository accountRepository;
    private final InternalAccountProvisioningRepository provisioningRepository;
    private final AccountLocationAssignmentRepository locationAssignmentRepository;
    private final AuditService auditService;

    @Transactional
    public Account bootstrapAccount(VerifiedIdentity identity, String correlationId) {
        String uid = identity.uid();

        Optional<Account> existingOpt = accountRepository.findByFirebaseUid(uid);
        if (existingOpt.isPresent()) {
            Account account = existingOpt.get();

            if (account.getStatus() == AccountStatus.SUSPENDED) {
                throw new AccountSuspendedException("Account is SUSPENDED for UID: " + uid);
            }

            // T029 Activation Path for PENDING_SETUP internal accounts
            if (account.getStatus() == AccountStatus.PENDING_SETUP) {
                Optional<InternalAccountProvisioning> provOpt = provisioningRepository.findByAccountId(account.getId());
                if (provOpt.isPresent()) {
                    InternalAccountProvisioning prov = provOpt.get();
                    if (identity.emailVerified()) {
                        // Check location scope is still active for internal roles
                        boolean hasActiveLocation = !locationAssignmentRepository
                            .findByAccountIdAndActiveTrue(account.getId()).isEmpty();

                        if (account.getRole() == AccountRole.STAFF || account.getRole() == AccountRole.MANAGER) {
                            if (!hasActiveLocation) {
                                throw new LocationValidationException("No active location assignment found for internal account");
                            }
                        }

                        AccountStatus prevStatus = account.getStatus();
                        account.setStatus(AccountStatus.ACTIVE);
                        if (identity.email() != null) {
                            account.setLoginEmail(identity.email());
                        }
                        prov.setStatus(InternalProvisioningStatus.SETUP_COMPLETE);
                        provisioningRepository.save(prov);
                        account = accountRepository.save(account);

                        auditService.recordEvent(
                            account.getId(),
                            account.getId(),
                            "ACCOUNT_ACTIVATED",
                            AuditOutcome.SUCCESS,
                            correlationId,
                            Map.of("previous_status", prevStatus.name(), "new_status", AccountStatus.ACTIVE.name())
                        );
                    }
                }
            }

            return account;
        }

        // Create new Member account (idempotent bootstrap)
        try {
            Account newAccount = Account.builder()
                .firebaseUid(uid)
                .loginEmail(identity.email())
                .emailVerifiedAt(identity.emailVerified() ? Instant.now() : null)
                .role(AccountRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .build();

            newAccount = accountRepository.save(newAccount);

            auditService.recordEvent(
                newAccount.getId(),
                newAccount.getId(),
                "ACCOUNT_CREATED",
                AuditOutcome.SUCCESS,
                correlationId,
                Map.of("new_role", AccountRole.MEMBER.name(), "new_status", AccountStatus.ACTIVE.name())
            );

            return newAccount;
        } catch (DataIntegrityViolationException ex) {
            // Concurrent insert race condition: fetch the existing row created by parallel request
            log.info("Concurrent insert conflict on firebaseUid={}, retrieving created account", uid);
            return accountRepository.findByFirebaseUid(uid)
                .orElseThrow(() -> new IllegalStateException("Account should exist after conflict", ex));
        }
    }
}
