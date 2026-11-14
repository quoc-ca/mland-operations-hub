package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeAllowanceResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GrantChangeAllowanceRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TicketChangeController {

    private final TicketChangeService changeService;

    @PostMapping("/api/v1/workshop-tickets/{ticketCode}/changes")
    public ResponseEntity<ChangeResultDto> changeTicketSessions(
            @PathVariable("ticketCode") String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody TicketChangeRequestDto request,
            Authentication authentication) {
        CurrentAccountActor actor = actor(authentication);
        ChangeResultDto result = changeService.changeTicketSessions(
                ticketCode, request, idempotencyKey, correlationId, actor
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/v1/workshop-tickets/{ticketCode}/change-allowances")
    public ResponseEntity<ChangeAllowanceResponseDto> grantExtraAllowance(
            @PathVariable("ticketCode") String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody GrantChangeAllowanceRequestDto request,
            Authentication authentication) {
        CurrentAccountActor actor = actor(authentication);
        ChangeAllowanceResponseDto result = changeService.grantExtraAllowance(
                ticketCode, request, idempotencyKey, correlationId, actor
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor actor
                ? actor : null;
    }
}

