package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.RequiredSessionChangeCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RequiredSessionChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workshop-bookings/{bookingCode}/required-session-change-cases")
@RequiredArgsConstructor
public class RequiredSessionChangeController {
    private final RequiredSessionChangeService service;

    @PostMapping
    public ResponseEntity<SettlementCaseProjectionDto> open(
            @PathVariable String bookingCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @Valid @RequestBody RequiredSessionChangeCommandDto command,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.open(bookingCode, idempotencyKey,
                correlation, command, actor(authentication)));
    }

    @GetMapping("/{caseReference}")
    public SettlementCaseProjectionDto read(
            @PathVariable String bookingCode,
            @PathVariable String caseReference,
            @RequestParam(required = false) String contactEmail,
            @RequestParam(required = false) String contactPhone,
            Authentication authentication) {
        return service.read(bookingCode, caseReference, contactEmail, contactPhone, actor(authentication));
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor actor
                ? actor : null;
    }
}
