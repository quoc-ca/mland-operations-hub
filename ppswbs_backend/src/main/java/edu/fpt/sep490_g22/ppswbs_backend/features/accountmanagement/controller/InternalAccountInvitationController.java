package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InvitationCompletionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InvitationExchangeRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.InvitationCompletionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.InvitationExchangeResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.InvitationExchangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InternalAccountInvitationController {

    private final InvitationExchangeService exchangeService;

    @PostMapping("/api/v1/internal-account-invitations/exchanges")
    public ResponseEntity<InvitationExchangeResponse> exchangeInvitation(
            @Valid @RequestBody InvitationExchangeRequest request
    ) {
        InvitationExchangeResponse response = exchangeService.exchangeSecret(request.getSecret());
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body(response);
    }

    @PostMapping("/api/v1/internal-account-invitations/completions")
    public ResponseEntity<InvitationCompletionResponse> completeInvitation(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
            @Valid @RequestBody InvitationCompletionRequest request,
            HttpServletRequest httpRequest
    ) {
        CurrentAccountActor actor = ActorContext.getCurrentActor().orElse(null);
        String correlationId = resolveCorrelationId(correlationHeader, httpRequest);

        InvitationCompletionResponse response = exchangeService.completeSetup(request, actor, correlationId);
        return ResponseEntity.ok(response);
    }

    private String resolveCorrelationId(String header, HttpServletRequest request) {
        if (header != null && !header.isBlank()) return header.trim();
        String reqHeader = request.getHeader("X-Correlation-Id");
        if (reqHeader != null && !reqHeader.isBlank()) return reqHeader.trim();
        return UUID.randomUUID().toString();
    }
}
