package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Actor/capability adapter over the 001 identity facade (T007, FR-001/002, interfaces "security").
 *
 * <p>The bearer is verified by 001's {@code IdentityGateFilter}, which puts the current actor (role and status
 * read from MySQL on every request) into the security context. This adapter only decides catalogue authority:
 * <ul>
 *   <li>No actor → {@code AUTHENTICATION_REQUIRED} (401). Not ACTIVE → {@code CATALOGUE_ACCESS_DENIED} (403).</li>
 *   <li>STAFF and MANAGER hold every management capability except the two decisions; only MANAGER may
 *       approve a product review or a price ({@code product.approve}, {@code price.approve}).</li>
 *   <li>ADMIN and MEMBER hold no catalogue capability (decision 2026-10-24). Nothing is granted from Firebase
 *       claims, cookies or a Member bootstrap.</li>
 * </ul>
 * Returns the opaque actor ID stored in receipts and audit: the decimal account ID.
 */
@Component
public class CatalogueAuthorization {

    /** Action vocabulary of the contract (interfaces "Capability names"). */
    public enum Capability {
        READ_INTERNAL, PRODUCT_CREATE, PRODUCT_EDIT, PRODUCT_SUBMIT, PRODUCT_APPROVE, PRODUCT_PUBLISH, PRODUCT_DELETE,
        MEDIA_MANAGE, VARIANT_QUANTITY, VARIANT_SALE_STATUS, PRICE_PROPOSE, PRICE_APPROVE, PRICE_APPLY,
        CATEGORY_MANAGE
    }

    private static final Set<Capability> MANAGER_ONLY = Set.of(Capability.PRODUCT_APPROVE, Capability.PRICE_APPROVE);

    /** The current actor's ID if they hold the capability; otherwise a typed 401/403. */
    public String require(Capability capability) {
        CurrentAccountActor actor = ActorContext.getCurrentActor()
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.AUTHENTICATION_REQUIRED));
        if (actor.accountId() == null || actor.status() != ActorStatus.ACTIVE || !allowed(actor.role(), capability)) {
            throw new CatalogueException(CatalogueErrorCode.CATALOGUE_ACCESS_DENIED);
        }
        return actor.accountId().toString();
    }

    static boolean allowed(ActorRole role, Capability capability) {
        if (role == ActorRole.MANAGER) {
            return true;
        }
        return role == ActorRole.STAFF && !MANAGER_ONLY.contains(capability);
    }
}
