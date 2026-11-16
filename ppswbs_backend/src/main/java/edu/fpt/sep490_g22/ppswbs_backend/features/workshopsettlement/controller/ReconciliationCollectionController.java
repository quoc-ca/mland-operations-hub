package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.Method;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.ReconciliationCollectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.State;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.ReconciliationCollectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** FR-056 (T133): the Manager's read-only transfer/VNPay/booking-time reconciliation list. */
@RestController
@RequiredArgsConstructor
public class ReconciliationCollectionController {

    private final ReconciliationCollectionService reconciliation;

    @GetMapping("/api/v1/workshop-reconciliation-collections")
    public List<ReconciliationCollectionDto> list(
            @RequestParam Long locationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workingDate,
            @RequestParam(required = false) Method method,
            @RequestParam(required = false) State state,
            Authentication authentication) {
        return reconciliation.list(locationId, workingDate, method, state, actor(authentication));
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
    }
}
