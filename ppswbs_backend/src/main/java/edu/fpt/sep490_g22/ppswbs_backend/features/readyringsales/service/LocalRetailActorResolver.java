package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class LocalRetailActorResolver implements RetailActorResolver {

    static final String DEBUG_ACCOUNT_ID_HEADER = "X-Debug-Account-Id";

    @Override
    public RetailMemberActor requireActiveMember(HttpServletRequest request) {
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
