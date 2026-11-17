package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;

import java.util.List;
import java.util.Optional;

public interface AccountFacade {

    Optional<AccountSummary> resolveAccount(String firebaseUid);

    Optional<ActorStatus> getAccountStatus(Long accountId);

    /**
     * The full name on the account's profile, trimmed, or empty when none is set. Contract for features that show a name
     * (for example Feature-011 reviews): the caller decides how much of it to reveal; it must not be exposed as is.
     */
    Optional<String> findAccountFullName(Long accountId);

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

    /**
     * Scope-filtered roster query returning ACTIVE STAFF candidates for a Workshop Location.
     * Manager selection is outside this roster contract.
     *
     * @param workshopLocationId authoritative Workshop Location ID
     * @return RosterOutcome containing eligible active Staff entries
     */
    RosterOutcome findEligibleRoster(Long workshopLocationId);

    /**
     * Backward compatibility overload for legacy callers.
     *
     * @deprecated Replaced by {@link #findEligibleRoster(Long)}. Roster returns only active STAFF candidates.
     */
    @Deprecated
    default RosterOutcome findEligibleRoster(Long workshopLocationId, AccountRole role) {
        if (role != AccountRole.STAFF) {
            return RosterOutcome.available(java.util.List.of());
        }
        return findEligibleRoster(workshopLocationId);
    }

    Optional<MemberAddressSnapshot> findDefaultShippingAddress(AccountId accountId);

    Optional<MemberAddressSnapshot> findShippingAddressById(AccountId accountId, Long addressId);

    /**
     * Cross-feature contract for Workshop Booking 003.1 (FR-040a per FR-045, approved 2026-11-14).
     *
     * <p>Returns login emails of every Account with role MANAGER and status ACTIVE (ADMIN excluded),
     * de-duplicated and sorted alphabetically. Empty list when none qualifies.
     * Read-only; creates no Account audit event.
     *
     * <p><b>PII usage constraint:</b> The caller MUST use the returned list only inside its
     * own transaction to compose internal alert email recipients. The list MUST NOT appear in
     * domain events, audit records, application or provider logs, or any consumer-owned persistence.
     *
     * @return de-duplicated and sorted List of active Manager login emails, or empty list
     */
    List<String> listActiveManagerEmails();
}
