package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.ManagerReasonRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketFolioDtos.CashReversalDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.CashReversalService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** T144 (FR-036, FR-053): Manager-only reversal of a cash collection of a closed working day. */
@RestController
@RequiredArgsConstructor
public class CashReversalController {

    private final CashReversalService reversals;

    @PostMapping("/api/v1/workshop-tickets/{ticketCode}/folio/collections/{collectionId}/cash-reversals")
    public ResponseEntity<CashReversalDto> reverse(
            @PathVariable String ticketCode,
            @PathVariable String collectionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody ManagerReasonRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reversals.reverse(ticketCode, collectionId,
                idempotencyKey, correlationId, request.reason(), actor(authentication)));
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
    }
}
