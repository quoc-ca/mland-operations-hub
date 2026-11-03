package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.RefundPolicyRevisionCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.RefundPolicyRevisionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.MlandCancellationPolicyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workshop-refund-policies")
@RequiredArgsConstructor
public class WorkshopRefundPolicyController {
    private final MlandCancellationPolicyService policies;

    @GetMapping
    public List<RefundPolicyRevisionResponse> list(Authentication authentication) {
        return policies.list(actor(authentication)).stream().map(RefundPolicyRevisionResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<RefundPolicyRevisionResponse> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RefundPolicyRevisionCommandDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RefundPolicyRevisionResponse.from(
                policies.create(actor(authentication), request.effectiveAt(), request.wholeBookingRefundPercent(), idempotencyKey)));
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor actor
                ? actor : null;
    }
}
