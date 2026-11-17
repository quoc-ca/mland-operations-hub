package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ChargeCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCorrectionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketFolioDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCorrectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketFolioApiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workshop-tickets/{ticketCode}")
@RequiredArgsConstructor
public class TicketFolioController {

    private final TicketFolioApiService folioApi;
    private final FolioCorrectionService correctionService;

    @GetMapping("/folio")
    public ResponseEntity<TicketFolioDto> readFolio(
            @PathVariable String ticketCode,
            Authentication authentication) {
        return ResponseEntity.ok(folioApi.readFolio(ticketCode, actor(authentication)));
    }

    @PostMapping("/folio-lookups")
    public ResponseEntity<TicketFolioDto> lookupFolio(
            @PathVariable String ticketCode,
            @Valid @RequestBody GuestFolioLookupRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(folioApi.lookupFolio(ticketCode, request));
    }

    @GetMapping("/folio/add-ons")
    public ResponseEntity<List<CatalogueAddOnOptionDto>> listAddOns(
            @PathVariable String ticketCode,
            Authentication authentication) {
        return ResponseEntity.ok(folioApi.listAddOns(ticketCode, actor(authentication)));
    }

    @PostMapping("/folio/charges")
    public ResponseEntity<TicketFolioDto> addCharge(
            @PathVariable String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody ChargeCommandDto command,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(folioApi.addCharge(ticketCode, idempotencyKey, correlationId, command, actor(authentication)));
    }

    @PostMapping("/folio/finalizations")
    public ResponseEntity<TicketFolioDto> finalizeFolio(
            @PathVariable String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            Authentication authentication) {
        return ResponseEntity.ok(folioApi.finalizeFolio(ticketCode, idempotencyKey, correlationId, actor(authentication)));
    }

    @PostMapping("/folio/debts")
    public ResponseEntity<Void> recordDebt(
            @PathVariable String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody FolioCollectionDtos.DebtRequest request,
            Authentication authentication) {
        folioApi.recordDebt(ticketCode, idempotencyKey, correlationId, request, actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/folio/debts/closures")
    public ResponseEntity<Void> closeDebt(
            @PathVariable String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody FolioCollectionDtos.ManagerReasonRequest request,
            Authentication authentication) {
        folioApi.closeDebt(ticketCode, idempotencyKey, correlationId, request, actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/folio/corrections")
    public ResponseEntity<FolioCorrectionDtos.Response> createCorrection(
            @PathVariable String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody FolioCorrectionDtos.CreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(correctionService.create(ticketCode, idempotencyKey, correlationId, request, actor(authentication)));
    }

    @GetMapping("/folio/corrections/{correctionReference}")
    public ResponseEntity<FolioCorrectionDtos.Response> readCorrection(
            @PathVariable String ticketCode,
            @PathVariable String correctionReference,
            Authentication authentication) {
        return ResponseEntity.ok(correctionService.read(ticketCode, correctionReference, actor(authentication)));
    }

    @PostMapping("/folio/collections")
    public ResponseEntity<CollectionDto> recordCollection(
            @PathVariable String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody TicketCollectionRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(folioApi.recordCollection(ticketCode, idempotencyKey, correlationId, request,
                        httpRequest.getRemoteAddr(), actor(authentication)));
    }

    @PostMapping("/folio/collections/{collectionId}/failures")
    public ResponseEntity<CollectionDto> failCollection(
            @PathVariable String ticketCode,
            @PathVariable String collectionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody FolioCollectionDtos.ManagerReasonRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(folioApi.failCollection(ticketCode, collectionId, idempotencyKey, correlationId,
                request, actor(authentication)));
    }

    @PostMapping("/folio/collections/{collectionId}/confirmations")
    public ResponseEntity<CollectionDto> confirmCollection(
            @PathVariable String ticketCode,
            @PathVariable String collectionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody ConfirmBankTransferRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(folioApi.confirmCollection(ticketCode, collectionId, idempotencyKey, correlationId, request, actor(authentication)));
    }

    @PostMapping("/folio/handover-exceptions")
    public ResponseEntity<Void> approveHandover(
            @PathVariable String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody FolioCollectionDtos.ManagerReasonRequest request,
            Authentication authentication) {
        folioApi.approveHandoverException(ticketCode, idempotencyKey, correlationId, request, actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/folio/lines/{lineId}/voids")
    public ResponseEntity<TicketFolioDto> voidLine(
            @PathVariable String ticketCode,
            @PathVariable String lineId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody FolioCollectionDtos.ManagerReasonRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(folioApi.voidLine(ticketCode, lineId, idempotencyKey, correlationId, request, actor(authentication)));
    }

    @PostMapping("/folio/collections/{collectionId}/voids")
    public ResponseEntity<TicketFolioDto> voidCollection(
            @PathVariable String ticketCode,
            @PathVariable String collectionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody FolioCollectionDtos.ManagerReasonRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(folioApi.voidCollection(ticketCode, collectionId, idempotencyKey, correlationId, request, actor(authentication)));
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        return null;
    }
}
