package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementAuditReviewDtos.SettlementAuditPage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementAuditReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 003.2 US7 / FR-052: {@code GET /api/v1/workshop-audit/settlement-records}. Active global Manager only; served by
 * 003.3 because it owns {@code settlement_business_audits}. Query values arrive as text and are parsed by the
 * service after the Manager guard.
 */
@RestController
@RequestMapping("/api/v1/workshop-audit")
@RequiredArgsConstructor
public class SettlementAuditReviewController {
    private final SettlementAuditReviewService service;

    @GetMapping("/settlement-records")
    public SettlementAuditPage getSettlementRecords(
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateReference,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String denied,
            Authentication authentication) {
        return service.readRecords(actor(authentication), actionCode, actorId, aggregateType, aggregateReference,
                from, to, cursor, limit, denied);
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor current
                ? current : null;
    }
}
