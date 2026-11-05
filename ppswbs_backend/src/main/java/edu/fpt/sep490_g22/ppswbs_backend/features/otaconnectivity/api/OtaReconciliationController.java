package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaReconciliationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaReconciliationActionResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/ota-reconciliation-work-items", produces = MediaType.APPLICATION_JSON_VALUE)
public class OtaReconciliationController {
    private final OtaAuthorizationService authorizationService;
    private final OtaReconciliationService reconciliationService;

    public OtaReconciliationController(OtaAuthorizationService authorizationService,
                                      OtaReconciliationService reconciliationService) {
        this.authorizationService = authorizationService;
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    public OtaReconciliationWorkItemListResponse list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "status", required = false) String status) {
        var actor = authorizationService.requireManagerOrAdmin(authorization);
        return new OtaReconciliationWorkItemListResponse(reconciliationService.listWorkItems(status, actor));
    }

    @PostMapping(value = "/{workItemId}/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OtaReconciliationWorkItemViewResponse> resolve(
            @PathVariable String workItemId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReconciliationResolutionRequest request) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 8 || idempotencyKey.trim().length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must contain 8 to 128 characters");
        }
        var actor = authorizationService.requireManagerOrAdmin(authorization);
        OtaReconciliationActionResult result = reconciliationService.resolveByPublicReference(workItemId,
                request.resolution(), request.reason(), idempotencyKey, actor);
        return ResponseEntity.ok(new OtaReconciliationWorkItemViewResponse(result.workItemId(), result.mappingId(),
                result.status(), result.correlationId(), result.safeSummary()));
    }
}
