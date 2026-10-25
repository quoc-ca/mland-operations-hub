package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
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
                a.getRole(),
                a.getStatus(),
                a.getLoginEmail(),
                a.getCreatedAt(),
                a.getUpdatedAt()
            ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountStatus> getAccountStatus(Long accountId) {
        return accountRepository.findById(accountId)
            .map(Account::getStatus);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getLocationScope(Long accountId) {
        return locationAssignmentRepository.findByAccountIdAndActiveTrue(accountId).stream()
            .map(AccountLocationAssignment::getLocationId)
            .toList();
    }
}
