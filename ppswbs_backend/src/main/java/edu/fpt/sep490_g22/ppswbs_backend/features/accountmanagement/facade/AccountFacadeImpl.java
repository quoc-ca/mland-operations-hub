package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountFacadeImpl implements AccountFacade {

    private final AccountRepository accountRepository;
    private final AccountLocationAssignmentRepository locationAssignmentRepository;

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

    private ActorRole mapRole(edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole role) {
        if (role == null) return ActorRole.MEMBER;
        return ActorRole.valueOf(role.name());
    }

    private ActorStatus mapStatus(edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus status) {
        if (status == null) return ActorStatus.ACTIVE;
        return ActorStatus.valueOf(status.name());
    }
}
