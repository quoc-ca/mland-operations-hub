package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.util.List;
import java.util.Optional;

public interface AccountFacade {

    Optional<AccountSummary> resolveAccount(String firebaseUid);

    Optional<ActorStatus> getAccountStatus(Long accountId);

    List<Long> getLocationScope(Long accountId);

    Optional<CurrentAccountActor> resolveActor(String firebaseUid, boolean emailVerified);
}
