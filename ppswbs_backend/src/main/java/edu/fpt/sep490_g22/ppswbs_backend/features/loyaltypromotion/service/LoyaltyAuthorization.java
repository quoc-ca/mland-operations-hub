package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import org.springframework.stereotype.Component;

/**
 * Capability adapter over the 001 identity facade (T007, FR-001, FR-040, interfaces section 3).
 *
 * <p>The bearer is verified by 001's identity gate, which puts the current actor (role and status read from
 * MySQL on every request) into the security context. This adapter only decides loyalty authority:
 * <ul>
 *   <li>No actor gives {@code AUTHENTICATION_REQUIRED} (401). An actor that is not ACTIVE, or whose role does
 *       not hold the capability, gives {@code ACCESS_DENIED} (403).</li>
 *   <li>Only MANAGER manages programmes and the points policy. ADMIN only manages accounts and grants roles, and
 *       STAFF has no FE-06 capability.</li>
 *   <li>Only MEMBER reads and uses their own wallet, saved vouchers and benefits. A Manager is not a Member and
 *       a Member is not a Manager: no role implies the other.</li>
 *   <li>V1 asks for no MFA or extra assurance tier (001, decided 2026-10-27). Nothing is granted from Firebase
 *       claims, cookies, email or a Member bootstrap.</li>
 * </ul>
 * The caller never supplies a member id: it comes from the verified actor, so a Member cannot read another
 * Member's data by changing an identifier.
 */
@Component
public class LoyaltyAuthorization {

    /** Capabilities of FE-06. */
    public enum Capability {
        MANAGE_PROGRAMS,
        MANAGE_POLICY,
        USE_OWN_BENEFITS
    }

    /** The current Manager's opaque actor reference (decimal account id) if they hold the capability. */
    public String requireManager(Capability capability) {
        if (capability == Capability.USE_OWN_BENEFITS) {
            throw new IllegalArgumentException("Not a manager capability: " + capability);
        }
        CurrentAccountActor actor = activeActor();
        if (actor.role() != ActorRole.MANAGER) {
            throw denied();
        }
        return actor.accountId().toString();
    }

    /** The current Member's id (the account id) when an ACTIVE Member is signed in. */
    public long requireMember() {
        CurrentAccountActor actor = activeActor();
        if (actor.role() != ActorRole.MEMBER) {
            throw denied();
        }
        return actor.accountId();
    }

    private static CurrentAccountActor activeActor() {
        CurrentAccountActor actor = ActorContext.getCurrentActor()
                .orElseThrow(() -> new LoyaltyException(LoyaltyErrorCode.AUTHENTICATION_REQUIRED,
                        "Authentication is required."));
        if (actor.accountId() == null || actor.status() != ActorStatus.ACTIVE) {
            throw denied();
        }
        return actor;
    }

    private static LoyaltyException denied() {
        return new LoyaltyException(LoyaltyErrorCode.ACCESS_DENIED, "You do not have access to this resource.");
    }
}
