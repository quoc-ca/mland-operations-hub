package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCollectionApiService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Branch cash close and difference confirmation (003.3 T094-T096, T106).
 */
@RestController
@RequiredArgsConstructor
public class FolioCollectionController {

    private final FolioCollectionApiService service;

    @GetMapping("/api/v1/workshop-cash-days/expected")
    public long expectedCash(
            @RequestParam Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDay,
            Authentication authentication) {
        return service.expectedCash(branchId, businessDay, actor(authentication));
    }

    @PostMapping("/api/v1/workshop-cash-days/close")
    public ResponseEntity<CashCloseResponse> closeCashDay(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody CashCloseRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.closeCashDay(request, correlation, actor(authentication)));
    }

    @PostMapping("/api/v1/workshop-cash-closes")
    public ResponseEntity<CashCloseResponse> closeCashBook(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody CashCloseRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.closeCashDay(request, correlation, actor(authentication)));
    }

    @PostMapping("/api/v1/workshop-cash-closes/{cashCloseId}/difference-confirmations")
    public CashCloseResponse confirmDifferenceById(
            @PathVariable String cashCloseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody ManagerReasonRequest request,
            Authentication authentication) {
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new WorkshopSettlementValidationException("Idempotency-Key must contain 16 to 128 characters");
        }
        return service.confirmDifferenceById(cashCloseId, request.reason(), correlation, actor(authentication));
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
    }
}
