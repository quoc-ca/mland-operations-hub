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
 * Checkout collections of one folio and the branch cash close (003.3 T094-T096).
 *
 * <p>{@code bookingReference} is a group ticket's code or a single booking's code: both have exactly one
 * folio, so one set of routes serves both.
 */
@RestController
@RequiredArgsConstructor
public class FolioCollectionController {

    private final FolioCollectionApiService service;

    @PostMapping("/api/v1/workshop-folios/{bookingReference}/collections/cash")
    public ResponseEntity<CollectionResponse> collectCash(
            @PathVariable String bookingReference,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody CashCollectionRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.collectCash(bookingReference, request, correlation, actor(authentication)));
    }

    @PostMapping("/api/v1/workshop-folios/{bookingReference}/collections/bank-transfer")
    public ResponseEntity<CollectionResponse> recordBankTransfer(
            @PathVariable String bookingReference,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody BankTransferRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.recordBankTransfer(bookingReference, request, correlation, actor(authentication)));
    }

    @PostMapping("/api/v1/workshop-folios/{bookingReference}/collections/gateway-attempt")
    public ResponseEntity<CollectionResponse> recordGatewayAttempt(
            @PathVariable String bookingReference,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody GatewayAttemptRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.recordGatewayAttempt(bookingReference, request, correlation, actor(authentication)));
    }

    @PostMapping("/api/v1/workshop-folio-collections/{collectionReference}/confirm-transfer")
    public CollectionResponse confirmBankTransfer(
            @PathVariable String collectionReference,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            Authentication authentication) {
        return service.confirmBankTransfer(collectionReference, correlation, actor(authentication));
    }

    @PostMapping("/api/v1/workshop-folio-collections/{collectionReference}/mark-failed")
    public CollectionResponse markFailed(
            @PathVariable String collectionReference,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            Authentication authentication) {
        return service.markPendingFailed(collectionReference, correlation, actor(authentication));
    }

    @PostMapping("/api/v1/workshop-folios/{bookingReference}/handover-exception")
    public ResponseEntity<Void> approveHandover(
            @PathVariable String bookingReference,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody ManagerReasonRequest request,
            Authentication authentication) {
        service.approveHandover(bookingReference, request, correlation, actor(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/workshop-folios/{bookingReference}/debt")
    public ResponseEntity<Void> recordDebt(
            @PathVariable String bookingReference,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody DebtRequest request,
            Authentication authentication) {
        service.recordDebt(bookingReference, request, correlation, actor(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/workshop-folios/{bookingReference}/debt/close")
    public ResponseEntity<Void> closeDebt(
            @PathVariable String bookingReference,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody ManagerReasonRequest request,
            Authentication authentication) {
        service.closeDebt(bookingReference, request, correlation, actor(authentication));
        return ResponseEntity.noContent().build();
    }

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

    @PostMapping("/api/v1/workshop-cash-days/confirm-difference")
    public CashCloseResponse confirmDifference(
            @RequestParam Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDay,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            Authentication authentication) {
        return service.confirmCashDifference(branchId, businessDay, correlation, actor(authentication));
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
    }
}
