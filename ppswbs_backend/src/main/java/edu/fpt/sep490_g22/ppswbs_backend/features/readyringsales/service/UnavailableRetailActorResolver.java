package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!local")
public class UnavailableRetailActorResolver implements RetailActorResolver {

    @Override
    public RetailMemberActor requireActiveMember(HttpServletRequest request) {
        throw new RetailIntegrationUnavailableException("Retail authentication integration is not configured");
    }
}
