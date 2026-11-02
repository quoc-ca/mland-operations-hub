package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AuditOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final AccountProfileRepository profileRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Optional<AccountProfile> getProfile(Long accountId) {
        // GET never creates or modifies profile
        return profileRepository.findByAccountId(accountId);
    }

    @Transactional
    public AccountProfile updateProfile(Long accountId, String fullName, String phone, String correlationId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new AccountSuspendedException("Account is SUSPENDED: " + accountId);
        }

        validateFullName(fullName);
        String trimmedPhone = validateAndTrimPhone(phone);

        // Pessimistic write lock on AccountProfile to serialize concurrent creation/updates
        Optional<AccountProfile> profileOpt = profileRepository.findForUpdateByAccountId(accountId);

        AccountProfile profile;
        if (profileOpt.isPresent()) {
            profile = profileOpt.get();
            profile.setFullName(fullName.trim());
            profile.setPhone(trimmedPhone);
            profile.setUpdatedAt(Instant.now());
        } else {
            profile = AccountProfile.builder()
                .accountId(accountId)
                .fullName(fullName.trim())
                .phone(trimmedPhone)
                .updatedAt(Instant.now())
                .build();
        }

        profile = profileRepository.save(profile);

        auditService.recordEvent(
            accountId,
            accountId,
            "PROFILE_UPDATED",
            AuditOutcome.SUCCESS,
            correlationId,
            null
        );

        return profile;
    }

    private void validateFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name must not be blank");
        }
        String trimmed = fullName.trim();
        int codePointCount = trimmed.codePointCount(0, trimmed.length());
        if (codePointCount < 1 || codePointCount > 100) {
            throw new IllegalArgumentException("Full name must be between 1 and 100 Unicode code points");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                throw new IllegalArgumentException("Full name must not contain ISO control characters");
            }
        }
    }

    private String validateAndTrimPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.length() < 7 || trimmed.length() > 30) {
            throw new IllegalArgumentException("Phone number must be between 7 and 30 characters after trimming");
        }
        return trimmed;
    }
}
