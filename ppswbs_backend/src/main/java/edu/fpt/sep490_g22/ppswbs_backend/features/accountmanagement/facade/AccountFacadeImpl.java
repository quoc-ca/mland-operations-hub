package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountFacadeImpl implements AccountFacade {

    private final AccountRepository accountRepository;
    private final AccountLocationAssignmentRepository locationAssignmentRepository;
    private final AccountProfileRepository profileRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountSummary> resolveAccount(String firebaseUid) {
        return accountRepository.findByFirebaseUid(firebaseUid)
            .map(a -> new AccountSummary(
                a.getId(),
                mapRole(a.getRole()),
                mapStatus(a.getStatus()),
                a.getLoginEmail(),
                a.getCreatedAt(),
                a.getUpdatedAt()
            ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActorStatus> getAccountStatus(Long accountId) {
        return accountRepository.findById(accountId)
            .map(a -> mapStatus(a.getStatus()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getLocationScope(Long accountId) {
        return locationAssignmentRepository.findByAccountIdAndActiveTrue(accountId).stream()
            .map(AccountLocationAssignment::getLocationId)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CurrentAccountActor> resolveActor(String firebaseUid, boolean emailVerified) {
        return accountRepository.findByFirebaseUid(firebaseUid)
            .map(account -> {
                List<Long> activeLocations = locationAssignmentRepository.findByAccountIdAndActiveTrue(account.getId()).stream()
                    .map(AccountLocationAssignment::getLocationId)
                    .toList();
                return new CurrentAccountActor(
                    account.getId(),
                    mapRole(account.getRole()),
                    mapStatus(account.getStatus()),
                    emailVerified,
                    activeLocations
                );
            });
    }

    /**
     * Provisional cross-feature contract for Workshop Operations 003.2,
     * created per FE-001 owner confirmation; FE-001 owner to review upon branch merge.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TargetAccountSnapshot> getTargetAccount(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return accountRepository.findById(accountId)
            .map(account -> {
                List<Long> activeLocations = locationAssignmentRepository.findByAccountIdAndActiveTrue(account.getId()).stream()
                    .map(AccountLocationAssignment::getLocationId)
                    .toList();
                return new TargetAccountSnapshot(
                    account.getId(),
                    mapRole(account.getRole()),
                    mapStatus(account.getStatus()),
                    activeLocations
                );
            });
    }

    @Override
    @Transactional(readOnly = true)
    public TargetFactsOutcome findTargetFacts(AccountId accountId) {
        if (accountId == null || accountId.value() == null) {
            return TargetFactsOutcome.notFound();
        }
        Optional<Account> accountOpt = accountRepository.findById(accountId.value());
        if (accountOpt.isEmpty()) {
            return TargetFactsOutcome.notFound();
        }
        Account account = accountOpt.get();
        Set<Long> activeLocs = locationAssignmentRepository.findByAccountIdAndActiveTrue(account.getId()).stream()
            .map(AccountLocationAssignment::getLocationId)
            .collect(Collectors.toSet());
        return TargetFactsOutcome.found(new TargetAccountFacts(
            accountId,
            account.getRole(),
            account.getStatus(),
            activeLocs
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public RosterOutcome findEligibleRoster(Long workshopLocationId, AccountRole role) {
        if (workshopLocationId == null || role == null || (role != AccountRole.STAFF && role != AccountRole.MANAGER)) {
            return RosterOutcome.available(List.of());
        }
        List<AccountLocationAssignment> assignments = locationAssignmentRepository.findByLocationIdAndActiveTrue(workshopLocationId);
        List<InternalRosterEntry> roster = new ArrayList<>();
        for (AccountLocationAssignment assignment : assignments) {
            Optional<Account> accountOpt = accountRepository.findById(assignment.getAccountId());
            if (accountOpt.isPresent()) {
                Account account = accountOpt.get();
                if (account.getStatus() == AccountStatus.ACTIVE && account.getRole() == role) {
                    String displayName = profileRepository.findByAccountId(account.getId())
                        .map(AccountProfile::getFullName)
                        .filter(name -> name != null && !name.isBlank())
                        .orElse(account.getLoginEmail() != null ? account.getLoginEmail().split("@")[0] : "Staff");
                    roster.add(new InternalRosterEntry(
                        AccountId.of(account.getId()),
                        displayName,
                        account.getRole()
                    ));
                }
            }
        }
        return RosterOutcome.available(roster);
    }

    private ActorRole mapRole(AccountRole role) {
        if (role == null) return ActorRole.MEMBER;
        return ActorRole.valueOf(role.name());
    }

    private ActorStatus mapStatus(AccountStatus status) {
        if (status == null) return ActorStatus.ACTIVE;
        return ActorStatus.valueOf(status.name());
    }
}
