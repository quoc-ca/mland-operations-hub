package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Production retail actor boundary. The identity gate has already verified the
 * Firebase token and resolved this actor through accountmanagement::facade.
 * This class deliberately trusts neither a request header nor a client-supplied
 * account identifier.
 */
@Service
@Profile("!local")
public class RetailAuthorization implements RetailActorResolver {

    static final String CURRENT_ACTOR_ATTRIBUTE = "currentAccountActor";

    @Override
    public RetailMemberActor requireActiveMember(HttpServletRequest request) {
        Object candidate = request.getAttribute(CURRENT_ACTOR_ATTRIBUTE);
        if (!(candidate instanceof CurrentAccountActor actor) || actor.accountId() == null) {
            throw new RetailUnauthorizedException("An authenticated retail actor is required");
        }
        if (actor.status() != ActorStatus.ACTIVE || actor.role() != ActorRole.MEMBER) {
            throw new RetailAccessDeniedException("Only an active Member can perform this retail action");
        }
        return new RetailMemberActor(actor.accountId());
    }
}
