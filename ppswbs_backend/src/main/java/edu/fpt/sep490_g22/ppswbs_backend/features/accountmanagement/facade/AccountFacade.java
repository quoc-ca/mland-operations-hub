package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;

import java.util.List;
import java.util.Optional;

public interface AccountFacade {

    Optional<AccountSummary> resolveAccount(String firebaseUid);

    Optional<AccountStatus> getAccountStatus(Long accountId);

    List<Long> getLocationScope(Long accountId);
}
