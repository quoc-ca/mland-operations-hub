package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.List;

/** Centralizes Settlement role/status/location checks without reading Firebase claims directly. */
@Service
@RequiredArgsConstructor
public class WorkshopSettlementAuthorizationService {
    private final AccountFacade accounts;
    private final SettlementBusinessAuditRepository auditRepository;

    public AccountSummary requireActive(String firebaseUid) {
        AccountSummary actor = accounts.resolveAccount(firebaseUid).orElse(null);
        if (actor == null) throw deny("UNKNOWN", "ACCOUNT_NOT_FOUND");
        if (actor.status() != ActorStatus.ACTIVE) {
            throw deny(actor.accountId().toString(), "ACCOUNT_NOT_ACTIVE");
        }
        return actor;
    }

    public AccountSummary requireManager(String firebaseUid) {
        AccountSummary actor = requireActive(firebaseUid);
        if (actor.role() != ActorRole.MANAGER) {
            throw deny(actor.accountId().toString(), "MANAGER_REQUIRED");
        }
        return actor;
    }

    public AccountSummary requireManagerOrStaff(String firebaseUid, Long bookingLocationId) {
        AccountSummary actor = requireActive(firebaseUid);
        if (actor.role() == ActorRole.MANAGER) return actor;
        if (actor.role() != ActorRole.STAFF || bookingLocationId == null
                || !accounts.getLocationScope(actor.accountId()).contains(bookingLocationId)) {
            throw deny(actor.accountId().toString(), "LOCATION_SCOPE_DENIED");
        }
        return actor;
    }

    /** Uses the canonical actor already resolved by IdentityGate for HTTP requests. */
    public CurrentAccountActor requireManager(CurrentAccountActor actor) {
        requireActive(actor);
        if (actor.role() != ActorRole.MANAGER) throw deny(actor.accountId().toString(), "MANAGER_REQUIRED");
        return actor;
    }

    /** Manager is global for Feature 003; Staff remains scoped to active locations. */
    public CurrentAccountActor requireManagerOrStaff(CurrentAccountActor actor, Long bookingLocationId) {
        requireActive(actor);
        if (actor.role() == ActorRole.MANAGER) return actor;
        if (actor.role() != ActorRole.STAFF || bookingLocationId == null
                || actor.activeLocationIds() == null || !actor.activeLocationIds().contains(bookingLocationId)) {
            throw deny(actor.accountId().toString(), "LOCATION_SCOPE_DENIED");
        }
        return actor;
    }

    private void requireActive(CurrentAccountActor actor) {
        if (actor == null || actor.accountId() == null) throw deny("UNKNOWN", "ACCOUNT_NOT_FOUND");
        if (actor.status() != ActorStatus.ACTIVE) throw deny(actor.accountId().toString(), "ACCOUNT_NOT_ACTIVE");
    }

    public void requireMemberOwnership(String firebaseUid, Long ownerAccountId) {
        AccountSummary actor = requireActive(firebaseUid);
        if (actor.role() != ActorRole.MEMBER || !actor.accountId().equals(ownerAccountId)) {
            throw deny(actor.accountId().toString(), "MEMBER_OWNERSHIP_REQUIRED");
        }
    }

    private WorkshopSettlementAuthorizationException deny(String actorReference, String reason) {
        auditRepository.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUTH-" + UUID.randomUUID())
                .aggregateType("AUTHORIZATION")
                .aggregateReference(actorReference)
                .actionCode("DENIED_" + reason)
                .actorReference(actorReference)
                .correlationReference("AUTH-" + UUID.randomUUID())
                .build());
        return new WorkshopSettlementAuthorizationException(reason);
    }
}
