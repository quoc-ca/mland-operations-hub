package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class LocalRetailActorResolver implements RetailActorResolver {

    static final String DEBUG_ACCOUNT_ID_HEADER = "X-Debug-Account-Id";
    static final String CURRENT_ACTOR_ATTRIBUTE = "currentAccountActor";

    @Override
    public RetailMemberActor requireActiveMember(HttpServletRequest request) {
        Object candidate = request.getAttribute(CURRENT_ACTOR_ATTRIBUTE);
        if (candidate instanceof CurrentAccountActor actor && actor.accountId() != null) {
            if (actor.status() != ActorStatus.ACTIVE || actor.role() != ActorRole.MEMBER) {
                throw new RetailAccessDeniedException("Only an active Member can perform this retail action");
            }
            return new RetailMemberActor(actor.accountId());
        }

        // Retain the local-only header fallback for direct API checks in Postman.
        String accountIdHeader = request.getHeader(DEBUG_ACCOUNT_ID_HEADER);
        if (accountIdHeader == null || accountIdHeader.isBlank()) {
            throw new RetailUnauthorizedException("Missing local development identity");
        }

        try {
            long accountId = Long.parseLong(accountIdHeader.trim());
            if (accountId <= 0) {
                throw new NumberFormatException("account ID must be positive");
            }
            return new RetailMemberActor(accountId);
        } catch (NumberFormatException exception) {
            throw new RetailUnauthorizedException("Invalid local development identity");
        }
    }
}
