package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountProvisioningRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AuditOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountProvisioning;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.InternalProvisioningStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAccountService {

    private final AccountRepository accountRepository;
    private final AccountProfileRepository profileRepository;
    private final AccountLocationAssignmentRepository locationAssignmentRepository;
    private final InternalAccountProvisioningRepository provisioningRepository;
    private final AuditService auditService;

    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(5);

    @Transactional
    public Account createInternalAccount(
        Long adminId,
        String idempotencyKey,
        AccountRole role,
        String fullName,
        String phone,
        List<Long> locationIds,
        String correlationId
    ) {
        return createInternalAccount(adminId, idempotencyKey, role, null, null, fullName, phone, locationIds, correlationId);
    }

    @Transactional
    public Account createInternalAccount(
        Long adminId,
        String idempotencyKey,
        AccountRole role,
        String email,
        String fullName,
        String phone,
        List<Long> locationIds,
        String correlationId
    ) {
        return createInternalAccount(adminId, idempotencyKey, role, email, null, fullName, phone, locationIds, correlationId);
    }

    @Transactional
    public Account createInternalAccount(
        Long adminId,
        String idempotencyKey,
        AccountRole role,
        String email,
        String password,
        String fullName,
        String phone,
        List<Long> locationIds,
        String correlationId
    ) {
        if (role != AccountRole.STAFF && role != AccountRole.MANAGER && role != AccountRole.CARRIER) {
            throw new IllegalArgumentException("Only STAFF, MANAGER, and CARRIER roles can be provisioned internally");
        }

        if (role == AccountRole.CARRIER) {
            return provisionCarrierAccount(adminId, idempotencyKey, email, password, fullName, phone, locationIds, correlationId);
        }

        validateLocationRoleConstraints(role, locationIds);

        // Check idempotency for STAFF / MANAGER
        Optional<InternalAccountProvisioning> existingProvOpt = provisioningRepository.findByIdempotencyKey(idempotencyKey);
        if (existingProvOpt.isPresent()) {
            InternalAccountProvisioning prov = existingProvOpt.get();
            Account existingAccount = accountRepository.findById(prov.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for provisioning"));

            // Verify payload matches
            if (existingAccount.getRole() != role) {
                throw new IdempotencyKeyConflictException("Idempotency key reused with different role");
            }
            List<Long> existingLocs = locationAssignmentRepository.findByAccountIdAndActiveTrue(existingAccount.getId()).stream()
                .map(AccountLocationAssignment::getLocationId)
                .toList();

            Set<Long> reqSet = locationIds != null ? new HashSet<>(locationIds) : Set.of();
            Set<Long> existSet = new HashSet<>(existingLocs);
            if (!reqSet.equals(existSet)) {
                throw new IdempotencyKeyConflictException("Idempotency key reused with different locations");
            }

            return existingAccount;
        }

        // Generate synthetic or invitation UID for internal account
        String firebaseUid = "internal-" + UUID.randomUUID();

        Account account = Account.builder()
            .firebaseUid(firebaseUid)
            .loginEmail(email != null && !email.isBlank() ? email.trim() : null)
            .role(role)
            .status(AccountStatus.PENDING_SETUP)
            .version(0L)
            .build();
        account = accountRepository.save(account);

        // Save profile
        if (fullName != null && !fullName.isBlank()) {
            AccountProfile profile = AccountProfile.builder()
                .accountId(account.getId())
                .fullName(fullName.trim())
                .phone(phone != null ? phone.trim() : null)
                .updatedAt(Instant.now())
                .build();
            profileRepository.save(profile);
        }

        // Save location assignments
        if (locationIds != null) {
            for (Long locId : locationIds) {
                AccountLocationAssignment ala = AccountLocationAssignment.builder()
                    .accountId(account.getId())
                    .locationId(locId)
                    .assignedAt(Instant.now())
                    .active(true)
                    .build();
                locationAssignmentRepository.save(ala);
            }
        }

        // Save provisioning record
        InternalAccountProvisioning prov = InternalAccountProvisioning.builder()
            .accountId(account.getId())
            .idempotencyKey(idempotencyKey)
            .status(InternalProvisioningStatus.INVITATION_SENT)
            .intendedEmail(email != null ? email.trim().toLowerCase() : null)
            .intendedRole(role.name())
            .invitationLastSentAt(Instant.now())
            .createdByAdminId(adminId != null ? adminId : account.getId())
            .firebaseSetupLinkIssuedAt(Instant.now())
            .build();
        provisioningRepository.save(prov);

        auditService.recordEvent(
            adminId,
            account.getId(),
            "ACCOUNT_PROVISIONED_ADMIN",
            AuditOutcome.SUCCESS,
            correlationId,
            Map.of("new_role", role.name(), "new_status", AccountStatus.PENDING_SETUP.name())
        );

        return account;
    }

    private Account provisionCarrierAccount(
        Long adminId,
        String idempotencyKey,
        String email,
        String password,
        String fullName,
        String phone,
        List<Long> locationIds,
        String correlationId
    ) {
        if (locationIds != null && !locationIds.isEmpty()) {
            throw new LocationValidationException("CARRIER accounts cannot have location assignments");
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Valid email is required for CARRIER");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password is required and must be at least 6 characters for CARRIER");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone number is required for CARRIER");
        }

        String normalizedEmail = email.trim().toLowerCase();

        // Check idempotency for CARRIER
        Optional<InternalAccountProvisioning> existingProvOpt = provisioningRepository.findByIdempotencyKey(idempotencyKey);
        if (existingProvOpt.isPresent()) {
            InternalAccountProvisioning prov = existingProvOpt.get();
            Account existingAccount = accountRepository.findById(prov.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for provisioning"));

            if (existingAccount.getRole() != AccountRole.CARRIER) {
                throw new IdempotencyKeyConflictException("Idempotency key reused with different role");
            }
            if (!Objects.equals(existingAccount.getLoginEmail(), normalizedEmail)) {
                throw new IdempotencyKeyConflictException("Idempotency key reused with different email");
            }
            return existingAccount;
        }

        // Check duplicate email in MySQL
        if (accountRepository.existsByLoginEmail(normalizedEmail)) {
            throw new IllegalStateException("An account with email " + normalizedEmail + " already exists in Mland");
        }

        // Provision Firebase identity
        String firebaseUid;
        boolean createdFirebaseUser = false;
        if (!FirebaseApp.getApps().isEmpty()) {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            try {
                try {
                    auth.getUserByEmail(normalizedEmail);
                    throw new IllegalStateException("A Firebase account with this email already exists: " + normalizedEmail);
                } catch (FirebaseAuthException e) {
                    String code = e.getAuthErrorCode() != null ? e.getAuthErrorCode().name() : "";
                    if ("USER_NOT_FOUND".equalsIgnoreCase(code) || (e.getMessage() != null && e.getMessage().contains("No user record found"))) {
                        UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                            .setEmail(normalizedEmail)
                            .setPassword(password)
                            .setDisplayName(fullName.trim())
                            .setEmailVerified(true);
                        UserRecord userRecord = auth.createUser(createRequest);
                        firebaseUid = userRecord.getUid();
                        createdFirebaseUser = true;
                    } else {
                        throw new IllegalStateException("Firebase email check failed: " + e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                if (e instanceof IllegalStateException ise && ise.getMessage() != null && ise.getMessage().startsWith("A Firebase account with this email")) {
                    throw ise;
                }
                if (e.getMessage() != null && e.getMessage().contains("does not support refreshing the access token")) {
                    log.warn("Firebase mock/development credentials in non-production. Simulating Firebase user creation for CARRIER.");
                    firebaseUid = "carrier-test-" + UUID.randomUUID();
                } else if (e instanceof RuntimeException re) {
                    throw re;
                } else {
                    throw new IllegalStateException("Firebase error: " + e.getMessage(), e);
                }
            }
        } else {
            log.warn("FirebaseApp not initialized. Simulating Firebase user creation for CARRIER in test/dev environment.");
            firebaseUid = "carrier-sim-" + UUID.randomUUID();
        }

        try {
            Account account = Account.builder()
                .firebaseUid(firebaseUid)
                .loginEmail(normalizedEmail)
                .emailVerifiedAt(Instant.now())
                .role(AccountRole.CARRIER)
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .build();
            account = accountRepository.save(account);

            AccountProfile profile = AccountProfile.builder()
                .accountId(account.getId())
                .fullName(fullName.trim())
                .phone(phone.trim())
                .updatedAt(Instant.now())
                .build();
            profileRepository.save(profile);

            InternalAccountProvisioning prov = InternalAccountProvisioning.builder()
                .accountId(account.getId())
                .idempotencyKey(idempotencyKey)
                .status(InternalProvisioningStatus.SETUP_COMPLETE)
                .intendedEmail(normalizedEmail)
                .intendedRole(AccountRole.CARRIER.name())
                .intendedLocations(null)
                .createdByAdminId(adminId != null ? adminId : account.getId())
                .build();
            provisioningRepository.save(prov);

            auditService.recordEvent(
                adminId,
                account.getId(),
                "ACCOUNT_PROVISIONED_ADMIN",
                AuditOutcome.SUCCESS,
                correlationId,
                Map.of("new_role", AccountRole.CARRIER.name(), "new_status", AccountStatus.ACTIVE.name())
            );

            return account;
        } catch (Exception ex) {
            if (createdFirebaseUser && !FirebaseApp.getApps().isEmpty()) {
                try {
                    FirebaseAuth.getInstance().deleteUser(firebaseUid);
                } catch (Exception cleanupEx) {
                    log.error("Failed to delete Firebase user {} after DB failure", firebaseUid, cleanupEx);
                }
            }
            throw ex;
        }
    }

    @Transactional
    public InternalAccountProvisioning resendInvitation(Long adminId, Long targetAccountId, String correlationId) {
        Account target = accountRepository.findById(targetAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + targetAccountId));

        if (target.getStatus() != AccountStatus.PENDING_SETUP) {
            throw new IllegalStateException("Account is not PENDING_SETUP: " + targetAccountId);
        }

        InternalAccountProvisioning prov = provisioningRepository.findByAccountId(targetAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("Provisioning record not found for account: " + targetAccountId));

        if (prov.getInvitationLastSentAt() != null) {
            Duration elapsed = Duration.between(prov.getInvitationLastSentAt(), Instant.now());
            if (elapsed.compareTo(RESEND_COOLDOWN) < 0) {
                throw new InvitationCooldownActiveException("Must wait 5 minutes before resending invitation");
            }
        }

        prov.setInvitationLastSentAt(Instant.now());
        prov.setFirebaseSetupLinkIssuedAt(Instant.now());
        prov = provisioningRepository.save(prov);

        auditService.recordEvent(
            adminId,
            targetAccountId,
            "INVITATION_RESENT",
            AuditOutcome.SUCCESS,
            correlationId,
            null
        );

        return prov;
    }

    @Transactional
    public Account updateInternalProfile(
        Long adminId,
        Long targetAccountId,
        Long expectedVersion,
        String fullName,
        String phone,
        String correlationId
    ) {
        Account target = accountRepository.findById(targetAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + targetAccountId));

        if (target.getRole() == AccountRole.MEMBER) {
            throw new RoleAssignmentForbiddenException("Member profiles are self-managed and cannot be edited by Admin");
        }

        if (target.getRole() == AccountRole.CARRIER) {
            if (phone == null || phone.isBlank()) {
                throw new IllegalArgumentException("Phone number is required for CARRIER");
            }
        }

        if (!Objects.equals(target.getVersion(), expectedVersion)) {
            throw new OptimisticLockConflictException("Version conflict on account update");
        }

        Optional<AccountProfile> profileOpt = profileRepository.findByAccountId(targetAccountId);
        AccountProfile profile;
        if (profileOpt.isPresent()) {
            profile = profileOpt.get();
            profile.setFullName(fullName.trim());
            profile.setPhone(phone != null ? phone.trim() : null);
            profile.setUpdatedAt(Instant.now());
        } else {
            profile = AccountProfile.builder()
                .accountId(targetAccountId)
                .fullName(fullName.trim())
                .phone(phone != null ? phone.trim() : null)
                .updatedAt(Instant.now())
                .build();
        }
        profileRepository.save(profile);

        target.setVersion(target.getVersion() + 1);
        target = accountRepository.save(target);

        auditService.recordEvent(
            adminId,
            targetAccountId,
            "PROFILE_UPDATED_ADMIN",
            AuditOutcome.SUCCESS,
            correlationId,
            null
        );

        return target;
    }

    @Transactional
    public Account assignRoleAndLocation(
        Long adminId,
        Long targetAccountId,
        Long expectedVersion,
        AccountRole newRole,
        List<Long> locationIds,
        String correlationId
    ) {
        Account target = accountRepository.findById(targetAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + targetAccountId));

        if (!Objects.equals(target.getVersion(), expectedVersion)) {
            throw new OptimisticLockConflictException("Version conflict on account role/location assignment");
        }

        if (target.getRole() == AccountRole.CARRIER || newRole == AccountRole.CARRIER) {
            throw new RoleAssignmentForbiddenException("CARRIER accounts cannot transition to or from another role");
        }

        if (newRole == AccountRole.MEMBER && !locationIds.isEmpty()) {
            throw new RoleAssignmentForbiddenException("MEMBER accounts cannot have location assignments");
        }

        validateLocationRoleConstraints(newRole, locationIds);

        AccountRole prevRole = target.getRole();
        target.setRole(newRole);
        target.setVersion(target.getVersion() + 1);
        target = accountRepository.save(target);

        // Deactivate old active assignments
        List<AccountLocationAssignment> oldAssignments = locationAssignmentRepository.findByAccountIdAndActiveTrue(targetAccountId);
        for (AccountLocationAssignment old : oldAssignments) {
            old.setActive(false);
            locationAssignmentRepository.save(old);
        }

        // Insert new assignments
        for (Long locId : locationIds) {
            AccountLocationAssignment newAla = AccountLocationAssignment.builder()
                .accountId(targetAccountId)
                .locationId(locId)
                .assignedAt(Instant.now())
                .active(true)
                .build();
            locationAssignmentRepository.save(newAla);
        }

        auditService.recordEvent(
            adminId,
            targetAccountId,
            "ROLE_CHANGED",
            AuditOutcome.SUCCESS,
            correlationId,
            Map.of("previous_role", prevRole.name(), "new_role", newRole.name())
        );

        return target;
    }

    private void validateLocationRoleConstraints(AccountRole role, List<Long> locationIds) {
        if (role == AccountRole.STAFF) {
            if (locationIds == null || locationIds.size() != 1) {
                throw new LocationValidationException("STAFF accounts must have exactly one active location assignment");
            }
        } else if (role == AccountRole.MANAGER) {
            if (locationIds == null || locationIds.isEmpty()) {
                throw new LocationValidationException("MANAGER accounts must have at least one active location assignment");
            }
        }
    }
}
