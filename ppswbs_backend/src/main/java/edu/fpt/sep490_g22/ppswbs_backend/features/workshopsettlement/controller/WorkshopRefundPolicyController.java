package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.RefundPolicyRevisionCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.RefundPolicyRevisionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.MlandCancellationPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workshop-refund-policies")
@RequiredArgsConstructor
public class WorkshopRefundPolicyController {
    private final MlandCancellationPolicyService policies;

    @GetMapping
    public List<RefundPolicyRevisionResponse> list(@RequestHeader("Authorization") String authorization) {
        return policies.list(authorization).stream().map(RefundPolicyRevisionResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<RefundPolicyRevisionResponse> create(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RefundPolicyRevisionCommandDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RefundPolicyRevisionResponse.from(
                policies.create(authorization, request.effectiveAt(), request.wholeBookingRefundPercent(), idempotencyKey)));
    }
}
