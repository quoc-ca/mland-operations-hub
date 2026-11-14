package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketCancellationDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketSettlementCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TicketCancellationController {

    private final TicketSettlementCancellationService cancellationService;

    @PostMapping("/api/v1/workshop-tickets/{ticketCode}/cancellations")
    public ResponseEntity<TicketCancellationDto> cancelTicket(
            @PathVariable("ticketCode") String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody CancellationRequestDto request,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        CurrentAccountActor actor = actor(authentication);
        TicketCancellationDto result = cancellationService.cancelTicket(
                ticketCode, request, idempotencyKey, correlationId, actor
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/api/v1/workshop-booking-groups/{groupCode}/cancellations")
    public ResponseEntity<GroupCancellationResponseDto> cancelGroup(
            @PathVariable("groupCode") String groupCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody GroupCancellationRequestDto request,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        CurrentAccountActor actor = actor(authentication);
        GroupCancellationResponseDto result = cancellationService.cancelGroup(
                groupCode, request, idempotencyKey, correlationId, actor
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.trim().length() < 16 || key.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        return null;
    }
}

