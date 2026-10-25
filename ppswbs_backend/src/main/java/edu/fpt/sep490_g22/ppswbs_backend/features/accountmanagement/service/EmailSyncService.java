package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailSyncService {

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    @Transactional
    public Account syncEmail(Long accountId, VerifiedIdentity identity, String correlationId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new AccountSuspendedException("Account is SUSPENDED: " + accountId);
        }

        if (!account.getFirebaseUid().equals(identity.uid())) {
            throw new InvalidTokenException("Token UID does not match account UID");
        }

        if (!identity.emailVerified()) {
            throw new EmailNotVerifiedException("Cannot sync unverified email from token");
        }

        if (identity.email() == null || identity.email().isBlank()) {
            throw new InvalidTokenException("Token does not contain an email claim");
        }

        account.setLoginEmail(identity.email().trim().toLowerCase());
        account = accountRepository.save(account);

        auditService.recordEvent(
            accountId,
            accountId,
            "EMAIL_SYNCED",
            "SUCCESS",
            correlationId,
            null
        );

        return account;
    }
}
