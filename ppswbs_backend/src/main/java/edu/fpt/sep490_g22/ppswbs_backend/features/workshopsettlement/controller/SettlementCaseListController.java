package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementCaseListService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workshop-settlement-cases")
@RequiredArgsConstructor
public class SettlementCaseListController {
    private final SettlementCaseListService service;

    @GetMapping
    public List<SettlementCaseProjectionDto> list(
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit,
            Authentication authentication) {
        return service.list(actor(authentication), caseType, status, limit);
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor current
                ? current : null;
    }
}
