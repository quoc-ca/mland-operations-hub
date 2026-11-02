package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;

import java.util.List;
import java.util.Optional;

public interface AccountFacade {

    Optional<AccountSummary> resolveAccount(String firebaseUid);

    Optional<ActorStatus> getAccountStatus(Long accountId);

    List<Long> getLocationScope(Long accountId);

    Optional<CurrentAccountActor> resolveActor(String firebaseUid, boolean emailVerified);

    /**
     * Provisional cross-feature contract for Workshop Operations 003.2,
     * created per FE-001 owner confirmation; FE-001 owner to review upon branch merge.
     *
     * <p>Queries target account semantic snapshot by account ID.
     * Returns an empty Optional if the account does not exist.
     *
     * @param accountId target account ID
     * @return Optional containing TargetAccountSnapshot if found, empty otherwise
     */
    Optional<TargetAccountSnapshot> getTargetAccount(Long accountId);

    /**
     * Provisional cross-feature contract for Workshop Operations 003.2,
     * created per FE-001 owner confirmation; FE-001 owner to review upon branch merge.
     *
     * <p>Convenience alias for {@link #getTargetAccount(Long)}.
     *
     * @param accountId target account ID
     * @return Optional containing TargetAccountSnapshot if found, empty otherwise
     */
    default Optional<TargetAccountSnapshot> resolveTargetAccount(Long accountId) {
        return getTargetAccount(accountId);
    }

    TargetFactsOutcome findTargetFacts(AccountId accountId);

    RosterOutcome findEligibleRoster(Long workshopLocationId, AccountRole role);
}
