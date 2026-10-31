package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ChargeCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FinalBillProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FinalizationCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopFinalBillApiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workshop-bookings/{bookingCode}/final-bill")
@RequiredArgsConstructor
public class WorkshopFinalBillController {
    private final WorkshopFinalBillApiService service;

    @GetMapping
    public FinalBillProjectionDto read(@PathVariable String bookingCode,
            @RequestParam(required = false) String contactEmail,
            @RequestParam(required = false) String contactPhone,
            Authentication authentication) {
        return service.read(bookingCode, contactEmail, contactPhone, authentication);
    }

    @PostMapping
    public ResponseEntity<FinalBillProjectionDto> create(@PathVariable String bookingCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(bookingCode, idempotencyKey, correlation, authentication));
    }

    @PostMapping("/charges")
    public FinalBillProjectionDto charge(@PathVariable String bookingCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody ChargeCommandDto command, Authentication authentication) {
        return service.addCharge(bookingCode, idempotencyKey, correlation, command, authentication);
    }

    @PostMapping("/finalizations")
    public FinalBillProjectionDto finalizeBill(@PathVariable String bookingCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody FinalizationCommandDto command, Authentication authentication) {
        return service.finalizeBill(bookingCode, idempotencyKey, correlation, command, authentication);
    }
}
