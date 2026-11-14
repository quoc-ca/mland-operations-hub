package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.TicketRemedyAnswerRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.TicketRemedyAnswerResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketMlandRemedyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TicketMlandRemedyController {

    private final TicketMlandRemedyService remedyService;

    @PostMapping("/api/v1/workshop-tickets/{ticketCode}/mland-remedy/answers")
    public ResponseEntity<TicketRemedyAnswerResponseDto> answerOffer(
            @PathVariable("ticketCode") String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody TicketRemedyAnswerRequestDto request,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        CurrentAccountActor actor = actor(authentication);
        TicketRemedyAnswerResponseDto result = remedyService.answerOffer(
                ticketCode, request, idempotencyKey, correlationId, actor
        );
        return ResponseEntity.ok(result);
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.trim().length() < 16 || key.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor actor
                ? actor : null;
    }
}

