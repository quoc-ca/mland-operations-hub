package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCorrectionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCorrectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workshop-bookings/{bookingCode}/folio-corrections")
@RequiredArgsConstructor
public class FolioCorrectionController {

    private final FolioCorrectionService service;

    @PostMapping
    public ResponseEntity<FolioCorrectionDtos.Response> create(
            @PathVariable String bookingCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody FolioCorrectionDtos.CreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(bookingCode, idempotencyKey, correlationId, request, actor(authentication)));
    }

    @GetMapping("/{correctionReference}")
    public FolioCorrectionDtos.Response read(
            @PathVariable String bookingCode,
            @PathVariable String correctionReference,
            @RequestParam(required = false) String contactEmail,
            @RequestParam(required = false) String contactPhone,
            Authentication authentication) {
        return service.read(bookingCode, correctionReference, contactEmail, contactPhone, actor(authentication));
    }

    private CurrentAccountActor actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor current ? current : null;
    }
}

