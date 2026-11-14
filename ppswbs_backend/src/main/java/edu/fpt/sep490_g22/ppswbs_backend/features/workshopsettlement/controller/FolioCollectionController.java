package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCollectionApiService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Branch cash close and difference confirmation (003.3 T094-T096, T106, T120).
 *
 * <p>T120 (FR-052, FR-022d): the cash-close resource is the only public family. The former
 * {@code /workshop-cash-days/expected} and {@code /workshop-cash-days/close} routes are retired; the close requires
 * and stores an {@code Idempotency-Key}, and the expected cash is read under the cash-close resource.
 */
@RestController
@RequiredArgsConstructor
public class FolioCollectionController {

    private final FolioCollectionApiService service;

    @GetMapping("/api/v1/workshop-cash-closes/expected-cash")
    public ExpectedCashResponse expectedCash(
            @RequestParam Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDay,
            Authentication authentication) {
        return service.expectedCash(branchId, businessDay, actor(authentication));
    }

    @PostMapping("/api/v1/workshop-cash-closes")
    public ResponseEntity<CashCloseResponse> closeCashBook(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody CashCloseRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.closeCashDay(request, idempotencyKey, correlation, actor(authentication)));
    }

    @PostMapping("/api/v1/workshop-cash-closes/{cashCloseId}/difference-confirmations")
    public CashCloseResponse confirmDifferenceById(
            @PathVariable String cashCloseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody ManagerReasonRequest request,
            Authentication authentication) {
        return service.confirmDifferenceById(cashCloseId, request.reason(), idempotencyKey, correlation,
                actor(authentication));
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
    }
}
