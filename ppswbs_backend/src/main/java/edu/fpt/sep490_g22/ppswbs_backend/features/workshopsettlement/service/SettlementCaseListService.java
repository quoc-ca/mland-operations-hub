package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementCaseListService {
    private final SettlementCaseRepository repository;
    private final WorkshopSettlementAuthorizationService authorization;

    public List<SettlementCaseProjectionDto> list(CurrentAccountActor actor,
                                                  String caseType,
                                                  String status,
                                                  Integer limit) {
        authorization.requireManager(actor);
        int requestedLimit = limit == null ? 100 : limit;
        if (requestedLimit < 1 || requestedLimit > 100) {
            throw new IllegalArgumentException("LIMIT_MUST_BE_BETWEEN_1_AND_100");
        }

        String normalizedType = normalize(caseType);
        String normalizedStatus = normalize(status);
        List<SettlementCase> cases;
        if (normalizedType != null && normalizedStatus != null) {
            cases = repository.findTop100ByCaseTypeAndLifecycleStatusOrderByCreatedAtDesc(
                    normalizedType, normalizedStatus);
        } else if (normalizedType != null) {
            cases = repository.findTop100ByCaseTypeOrderByCreatedAtDesc(normalizedType);
        } else if (normalizedStatus != null) {
            cases = repository.findTop100ByLifecycleStatusOrderByCreatedAtDesc(normalizedStatus);
        } else {
            cases = repository.findTop100ByOrderByCreatedAtDesc();
        }
        return cases.stream().limit(requestedLimit).map(SettlementCaseProjectionDto::from).toList();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
