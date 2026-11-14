package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeAllowanceResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDecisionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GrantChangeAllowanceRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GroupTicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GroupTicketChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.GroupTicketChangeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeRequestService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TicketChangeController {

    private final TicketChangeService changeService;
    private final TicketChangeRequestService requestService;
    private final GroupTicketChangeService groupChangeService;

    /** 200 when applied or when the buyer must confirm an increase; 202 when it waits in the Manager queue (T117). */
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
        return result.changeRequestReference() != null && !result.applied()
                ? ResponseEntity.status(HttpStatus.ACCEPTED).body(result)
                : ResponseEntity.ok(result);
    }

    /** Several tickets of one group, all or none (T118, FR-044). */
    @PostMapping("/api/v1/workshop-booking-groups/{groupCode}/ticket-changes")
    public ResponseEntity<GroupTicketChangeResultDto> changeGroupTickets(
            @PathVariable("groupCode") String groupCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody GroupTicketChangeRequestDto request,
            Authentication authentication) {
        return ResponseEntity.ok(groupChangeService.changeGroupTickets(
                groupCode, request, idempotencyKey, correlationId, actor(authentication)));
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

    /** The Staff/Manager queue of changes only a Manager may allow (T117, FR-045a). */
    @GetMapping("/api/v1/workshop-change-requests")
    public List<ChangeRequestDto> listChangeRequests(
            @RequestParam(value = "status", required = false) String status,
            Authentication authentication) {
        return requestService.list(actor(authentication), status);
    }

    /** Manager approves or rejects a queued change, with a reason (T117). */
    @PostMapping("/api/v1/workshop-change-requests/{requestReference}/decisions")
    public ResponseEntity<ChangeRequestDto> decideChangeRequest(
            @PathVariable("requestReference") String requestReference,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody ChangeRequestDecisionDto request,
            Authentication authentication) {
        return ResponseEntity.ok(requestService.decide(
                requestReference, request, idempotencyKey, correlationId, actor(authentication)));
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor actor
                ? actor : null;
    }
}
