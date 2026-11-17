package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.OtaAddonPaymentDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.OtaAddonPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ota-addon-bills/{billReference}")
@RequiredArgsConstructor
public class OtaAddonPaymentController {

    private final OtaAddonPaymentService service;

    @GetMapping
    public ResponseEntity<OtaAddonBillProjection> getBillStatus(
            @PathVariable String billReference,
            Authentication authentication) {
        return ResponseEntity.ok(service.getBillStatus(billReference, actor(authentication)));
    }

    @PostMapping("/payment-attempts")
    public ResponseEntity<OtaAddonPaymentAttemptResponse> startPaymentAttempt(
            @PathVariable String billReference,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            HttpServletRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.startPaymentAttempt(billReference, idempotencyKey, correlationId, request.getRemoteAddr(), actor(authentication)));
    }

    @PostMapping("/refunds")
    public ResponseEntity<OtaAddonRefundProjection> createRefund(
            @PathVariable String billReference,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody OtaAddonRefundCommand command,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createRefund(billReference, idempotencyKey, correlationId, command, actor(authentication)));
    }

    private CurrentAccountActor actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor current ? current : null;
    }
}

