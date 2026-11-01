package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class WorkshopPackageAuthorizationService {

    private final WorkshopOperationsAuthorizationService authenticationService;

    /**
     * Requires an active canonical Manager. Manager authority is global
     * throughout Feature 003 and does not depend on location assignments.
     *
     * @return the canonical account ID for safe audit attribution
     */
    public Long requireActiveManager(String authorizationHeader) {
        AccountSummary authenticated = authenticationService.authenticate(authorizationHeader);
        if (authenticated.role() != ActorRole.MANAGER || authenticated.status() != ActorStatus.ACTIVE) {
            throw new WorkshopForbiddenException("Access requires an active Manager");
        }
        return authenticated.accountId();
    }
}
