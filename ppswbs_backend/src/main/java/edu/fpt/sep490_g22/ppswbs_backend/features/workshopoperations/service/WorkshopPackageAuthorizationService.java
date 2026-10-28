package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.TargetAccountSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkshopPackageAuthorizationService {

    private final AccountFacade accountFacade;
    private final WorkshopOperationsAuthorizationService authenticationService;
    private final WorkshopLocationRepository locationRepository;

    /**
     * Requires the V1 Central Manager policy: the active canonical Manager must
     * be assigned every currently active canonical Workshop Location.
     *
     * @return the canonical account ID for safe audit attribution
     */
    public Long requireCentralManager(String authorizationHeader) {
        AccountSummary authenticated = authenticationService.authenticate(authorizationHeader);
        TargetAccountSnapshot actor = accountFacade.getTargetAccount(authenticated.accountId())
                .filter(snapshot -> snapshot.status() == ActorStatus.ACTIVE
                        && snapshot.canonicalRole() == ActorRole.MANAGER)
                .orElseThrow(() -> new WorkshopForbiddenException("Access requires an active Central Manager"));

        List<Long> everyActiveLocationId = locationRepository.findByActiveTrue().stream()
                .map(location -> location.getId())
                .toList();
        if (!actor.activeLocationIds().containsAll(everyActiveLocationId)) {
            throw new WorkshopForbiddenException("Access requires an active Manager assigned to every active Workshop Location");
        }
        return actor.accountId();
    }
}
