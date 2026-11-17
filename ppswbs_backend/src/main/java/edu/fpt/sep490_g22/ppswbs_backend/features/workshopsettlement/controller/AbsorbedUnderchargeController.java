package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.AbsorbedUnderchargeDtos.AbsorbedUnderchargeDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.AbsorbedUnderchargeDtos.AbsorbedUnderchargeListDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.AbsorbedUnderchargeDtos.RecordRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.ManagerReasonRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.AbsorbedUnderchargeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** FR-056/FR-057 (T135): the Manager's absorbed-undercharge records of FINALIZED ticket folios. */
@RestController
@RequiredArgsConstructor
public class AbsorbedUnderchargeController {

    private final AbsorbedUnderchargeService undercharges;

    @PostMapping("/api/v1/workshop-tickets/{ticketCode}/folio/absorbed-undercharges")
    public ResponseEntity<AbsorbedUnderchargeDto> record(
            @PathVariable String ticketCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody RecordRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(undercharges.record(ticketCode, idempotencyKey,
                correlationId, request.amount(), request.reason(), actor(authentication)));
    }

    @PostMapping("/api/v1/workshop-tickets/{ticketCode}/folio/absorbed-undercharges/{underchargeReference}/voids")
    public AbsorbedUnderchargeDto voidRecord(
            @PathVariable String ticketCode,
            @PathVariable String underchargeReference,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody ManagerReasonRequest request,
            Authentication authentication) {
        return undercharges.voidRecord(ticketCode, underchargeReference, idempotencyKey, correlationId,
                request.reason(), actor(authentication));
    }

    @GetMapping("/api/v1/workshop-absorbed-undercharges")
    public AbsorbedUnderchargeListDto list(
            @RequestParam Long locationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return undercharges.list(locationId, from, to, actor(authentication));
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
    }
}
