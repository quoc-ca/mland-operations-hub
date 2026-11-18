package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AuditReviewDtos.OperationsAuditPage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AuditReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 003.2 US7 (FR-047 to FR-051): {@code GET /api/v1/workshop-audit/operations-events}. Active global Manager only;
 * Staff, Admin and every other role get 403 and the refused attempt is audited DENIED. The matching Settlement
 * route lives in 003.3 ({@code workshopsettlement}); Operations never calls it. Query values arrive as text and
 * are parsed by the service after the Manager guard, so a bad value is the existing 400 {@code VALIDATION_ERROR}.
 */
@RestController
@RequestMapping("/api/v1/workshop-audit")
@RequiredArgsConstructor
public class AuditReviewController {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final AuditReviewService auditReviewService;

    @GetMapping("/operations-events")
    public OperationsAuditPage getOperationsEvents(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationReference,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String limit) {
        return auditReviewService.readOperationsEvents(authorization, correlationReference, action, outcome,
                actorId, sessionId, from, to, cursor, limit);
    }
}
