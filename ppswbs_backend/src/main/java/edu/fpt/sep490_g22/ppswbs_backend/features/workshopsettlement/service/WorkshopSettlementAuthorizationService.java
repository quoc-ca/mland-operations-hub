package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Centralizes Settlement role/status/location checks without reading Firebase claims directly. */
@Service
@RequiredArgsConstructor
public class WorkshopSettlementAuthorizationService {
    private final AccountFacade accounts;

    public AccountSummary requireActive(String firebaseUid) {
        AccountSummary actor = accounts.resolveAccount(firebaseUid)
                .orElseThrow(() -> new WorkshopSettlementAuthorizationException("ACCOUNT_NOT_FOUND"));
        if (actor.status() != ActorStatus.ACTIVE) {
            throw new WorkshopSettlementAuthorizationException("ACCOUNT_NOT_ACTIVE");
        }
        return actor;
    }

    public AccountSummary requireManager(String firebaseUid) {
        AccountSummary actor = requireActive(firebaseUid);
        if (actor.role() != ActorRole.MANAGER) {
            throw new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED");
        }
        return actor;
    }

    public AccountSummary requireManagerOrStaff(String firebaseUid, Long bookingLocationId) {
        AccountSummary actor = requireActive(firebaseUid);
        if (actor.role() == ActorRole.MANAGER) return actor;
        if (actor.role() != ActorRole.STAFF || bookingLocationId == null
                || !accounts.getLocationScope(actor.accountId()).contains(bookingLocationId)) {
            throw new WorkshopSettlementAuthorizationException("LOCATION_SCOPE_DENIED");
        }
        return actor;
    }

    public void requireMemberOwnership(String firebaseUid, Long ownerAccountId) {
        AccountSummary actor = requireActive(firebaseUid);
        if (actor.role() != ActorRole.MEMBER || !actor.accountId().equals(ownerAccountId)) {
            throw new WorkshopSettlementAuthorizationException("MEMBER_OWNERSHIP_REQUIRED");
        }
    }
}
