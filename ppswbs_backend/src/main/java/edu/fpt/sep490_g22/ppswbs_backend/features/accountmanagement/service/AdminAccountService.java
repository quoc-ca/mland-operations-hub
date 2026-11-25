package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountProvisioningRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.MemberAddressRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AuditOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountProvisioning;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.MemberAddress;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.InternalProvisioningStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
    private final InvitationService invitationService;
    private final MemberAddressRepository addressRepository;
    private final ObjectProvider<RetailMemberReplacementReader> retailReaderProvider;
    private final ObjectProvider<WorkshopMemberReplacementReader> workshopReaderProvider;

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

        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Valid normalized email is required for STAFF and MANAGER");
        }
        String normalizedEmail = email.trim().toLowerCase();

        List<Long> sortedLocs = locationIds != null ? locationIds.stream().distinct().sorted().toList() : List.of();
        String requestDigest = InvitationService.sha256Hex(normalizedEmail + ":" + role.name() + ":" + sortedLocs);

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
            if (!Objects.equals(existingAccount.getLoginEmail(), normalizedEmail)) {
                throw new IdempotencyKeyConflictException("Idempotency key reused with different email");
            }
            List<Long> existingLocs = locationAssignmentRepository.findByAccountIdAndActiveTrue(existingAccount.getId()).stream()
                .map(AccountLocationAssignment::getLocationId)
                .sorted()
                .toList();

            if (!sortedLocs.equals(existingLocs)) {
                throw new IdempotencyKeyConflictException("Idempotency key reused with different locations");
            }
            if (prov.getRequestDigest() != null && !prov.getRequestDigest().equals(requestDigest)) {
                throw new IdempotencyKeyConflictException("Idempotency key reused with different payload");
            }

            return existingAccount;
        }

        // Check duplicate email in MySQL
        if (accountRepository.existsByLoginEmail(normalizedEmail)) {
            throw new IllegalStateException("An account with email " + normalizedEmail + " already exists in Mland");
        }

        // Provision real Firebase identity
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
                            .setDisplayName(fullName != null ? fullName.trim() : "")
                            .setEmailVerified(false);
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
                log.warn("Firebase mock in non-prod, simulating UID: {}", e.getMessage());
                firebaseUid = "inv-test-" + UUID.randomUUID();
            }
        } else {
            firebaseUid = "inv-sim-" + UUID.randomUUID();
        }

        try {
            Account account = Account.builder()
                .firebaseUid(firebaseUid)
                .loginEmail(normalizedEmail)
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
                .requestDigest(requestDigest)
                .status(InternalProvisioningStatus.INVITATION_SENT)
                .intendedEmail(normalizedEmail)
                .intendedRole(role.name())
                .invitationLastSentAt(Instant.now())
                .createdByAdminId(adminId != null ? adminId : account.getId())
                .firebaseSetupLinkIssuedAt(Instant.now())
                .build();
            provisioningRepository.save(prov);

            // Issue mandatory one-time invitation (version 1)
            invitationService.issueInitialInvitation(account, adminId, correlationId);

            auditService.recordEvent(
                adminId,
                account.getId(),
                "ACCOUNT_PROVISIONED_ADMIN",
                AuditOutcome.SUCCESS,
                correlationId,
                Map.of("new_role", role.name(), "new_status", AccountStatus.PENDING_SETUP.name())
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

        // Delegate cooldown and rotation to InvitationService (invalidates old, starts new 48h validity)
        invitationService.rotateInvitation(target, adminId, correlationId);

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

    @Transactional(readOnly = true)
    public EmailCollisionResponse resolveEmailCollision(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        String normalizedEmail = email.trim().toLowerCase();

        Optional<Account> accountOpt = accountRepository.findByLoginEmail(normalizedEmail);
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            String outcome;
            if (account.getRole() == AccountRole.STAFF || account.getRole() == AccountRole.MANAGER) {
                outcome = "STAFF_MANAGER_MAINTENANCE";
            } else if (account.getRole() == AccountRole.MEMBER) {
                outcome = "MEMBER_REVIEW";
            } else {
                outcome = "BLOCKED_ROLE";
            }
            return EmailCollisionResponse.builder()
                .normalizedEmail(normalizedEmail)
                .outcome(outcome)
                .accountId(account.getId())
                .build();
        }

        // Check Firebase identity occupancy
        if (!FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseAuth.getInstance().getUserByEmail(normalizedEmail);
                return EmailCollisionResponse.builder()
                    .normalizedEmail(normalizedEmail)
                    .outcome("FIREBASE_ONLY")
                    .accountId(null)
                    .build();
            } catch (FirebaseAuthException e) {
                // Expected when user does not exist in Firebase
            } catch (Exception e) {
                log.warn("Firebase occupancy check error: {}", e.getMessage());
            }
        }

        return EmailCollisionResponse.builder()
            .normalizedEmail(normalizedEmail)
            .outcome("NONE")
            .accountId(null)
            .build();
    }

    @Transactional(readOnly = true)
    public ReplacementReviewResponse reviewMemberReplacement(Long accountId, Long adminId, String correlationId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (account.getRole() != AccountRole.MEMBER) {
            throw new RoleAssignmentForbiddenException("Target account is not a replaceable MEMBER");
        }

        AccountProfile profile = profileRepository.findByAccountId(accountId)
            .orElse(AccountProfile.builder().accountId(accountId).fullName("").build());

        Map<String, Object> profileMap = Map.of(
            "fullName", profile.getFullName() != null ? profile.getFullName() : "",
            "phone", profile.getPhone() != null ? profile.getPhone() : "",
            "email", account.getLoginEmail() != null ? account.getLoginEmail() : "",
            "status", account.getStatus().name()
        );

        List<Map<String, Object>> addressList = addressRepository
            .findAllByAccountIdOrderByIsDefaultDescUpdatedAtDesc(accountId).stream()
            .map(a -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", a.getId());
                m.put("recipientName", a.getRecipientName());
                m.put("phoneNumber", a.getPhoneNumber());
                m.put("provinceName", a.getProvinceName());
                m.put("communeName", a.getCommuneName());
                m.put("detailAddress", a.getDetailAddress());
                m.put("isDefault", a.isDefault());
                return m;
            })
            .toList();

        RetailMemberReplacementReader retailReader = retailReaderProvider.getIfAvailable();
        RetailReplacementInventory retail = retailReader != null
            ? retailReader.readForMemberReplacement(new AccountId(accountId))
            : new RetailReplacementInventory(new AccountId(accountId), MemberReplacementInventoryStatus.UNAVAILABLE, MemberReplacementDataOrigin.NONE, null, Instant.now(), List.of(), List.of(), List.of());

        WorkshopMemberReplacementReader workshopReader = workshopReaderProvider.getIfAvailable();
        WorkshopReplacementInventory workshop = workshopReader != null
            ? workshopReader.readForMemberReplacement(new AccountId(accountId))
            : new WorkshopReplacementInventory(new AccountId(accountId), MemberReplacementInventoryStatus.UNAVAILABLE, MemberReplacementDataOrigin.NONE, null, Instant.now(), List.of(), List.of());

        Instant now = Instant.now();

        auditService.recordEvent(
            adminId,
            accountId,
            "MEMBER_REPLACEMENT_REVIEWED",
            AuditOutcome.SUCCESS,
            correlationId,
            Map.of("account_id", accountId)
        );

        return ReplacementReviewResponse.builder()
            .accountId(account.getId())
            .accountVersion(account.getVersion())
            .normalizedEmail(account.getLoginEmail())
            .profile(profileMap)
            .addresses(addressList)
            .retail(retail)
            .workshop(workshop)
            .generatedAt(now)
            .build();
    }

    @Transactional
    public FirebaseOnlyDeleteResponse deleteFirebaseOnlyCollision(
        String normalizedEmail,
        String occupancyReference,
        boolean confirm,
        Long adminId,
        String correlationId
    ) {
        if (!confirm) {
            throw new IllegalArgumentException("Explicit confirmation is required to delete Firebase-only identity");
        }

        String email = normalizedEmail.trim().toLowerCase();
        if (accountRepository.existsByLoginEmail(email)) {
            throw new IllegalStateException("An Mland Account owns this email. Cannot delete via Firebase-only flow.");
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            try {
                UserRecord user = FirebaseAuth.getInstance().getUserByEmail(email);
                FirebaseAuth.getInstance().deleteUser(user.getUid());
            } catch (FirebaseAuthException e) {
                log.warn("Firebase user deletion notice: {}", e.getMessage());
            }
        }

        auditService.recordEvent(
            adminId,
            null,
            "FIREBASE_ONLY_IDENTITY_DELETED",
            AuditOutcome.SUCCESS,
            correlationId,
            Map.of("email", email, "occupancy_reference", occupancyReference != null ? occupancyReference : "")
        );

        return new FirebaseOnlyDeleteResponse("DELETED");
    }
}
