package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopResponsibilityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * T018: REST controller for Workshop Slot editing and Workshop Session management endpoints.
 * Corresponds to OpenAPI paths:
 * - PATCH /api/v1/workshop-slots/{slotId}
 * - GET   /api/v1/workshop-sessions
 * - POST  /api/v1/workshop-sessions
 * - POST  /api/v1/workshop-sessions/bulk
 * - GET   /api/v1/workshop-sessions/{sessionId}
 * - PATCH /api/v1/workshop-sessions/{sessionId}
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkshopSessionController {

    private final WorkshopSlotService slotService;
    private final WorkshopSessionService sessionService;
    private final WorkshopLifecycleService lifecycleService;
    private final WorkshopResponsibilityService responsibilityService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.OperationsRestIdempotencyService restIdempotency;

    @PatchMapping("/workshop-slots/{slotId}")
    public ResponseEntity<SlotResponse> updateSlot(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("slotId") String slotId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody SlotRequest request) {
        SlotResponse response = restIdempotency.execute(idempotencyKey, "REST:PATCH:/api/v1/workshop-slots/{slotId}", "slot:update:" + slotId,
                request, SlotResponse.class, () -> slotService.updateSlot(authHeader, slotId, request));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/workshop-sessions")
    public ResponseEntity<List<SessionResponse>> searchSessions(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "locationId", required = false) Long locationId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "state", required = false) SessionState state) {
        List<SessionResponse> response = sessionService.searchSessions(authHeader, locationId, from, to, state);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/workshop-sessions")
    public ResponseEntity<SessionResponse> createSession(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody SessionRequest request) {
        SessionResponse response = restIdempotency.execute(idempotencyKey, "REST:POST:/api/v1/workshop-sessions", "session:create", request,
                SessionResponse.class, () -> sessionService.createSession(authHeader, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/workshop-sessions/bulk")
    public ResponseEntity<BulkGenerationResponse> generateBulkSessions(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody BulkSessionRequest request) {
        BulkGenerationResponse response = restIdempotency.execute(idempotencyKey, "REST:POST:/api/v1/workshop-sessions/bulk", "session:bulk-create", request,
                BulkGenerationResponse.class, () -> sessionService.generateBulkSessions(authHeader, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/workshop-sessions/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("sessionId") String sessionId) {
        SessionResponse response = sessionService.getSessionByReference(authHeader, sessionId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/workshop-sessions/{sessionId}")
    public ResponseEntity<SessionResponse> updateSession(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody SessionRequest request) {
        SessionResponse response = restIdempotency.execute(idempotencyKey, "REST:PATCH:/api/v1/workshop-sessions/{sessionId}", "session:update:" + sessionId,
                request, SessionResponse.class, () -> sessionService.updateSession(authHeader, sessionId, request));
        return ResponseEntity.ok(response);
    }

    /** 003.2 FR-035 / T095: Manager sets or clears one session's reschedule quota. */
    @PutMapping("/workshop-sessions/{sessionId}/reschedule-quota")
    public ResponseEntity<SessionResponse> setRescheduleQuota(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody RescheduleQuotaRequest request) {
        SessionResponse response = restIdempotency.execute(idempotencyKey,
                "REST:PUT:/api/v1/workshop-sessions/{sessionId}/reschedule-quota",
                "session:reschedule-quota:" + sessionId,
                request, SessionResponse.class,
                () -> sessionService.setRescheduleQuota(authHeader, sessionId, request));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/workshop-sessions/{sessionId}/state-transitions")
    public ResponseEntity<SessionResponse> applyStateTransition(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody StateTransitionRequest request) {
        SessionResponse response = restIdempotency.execute(idempotencyKey, "REST:POST:/api/v1/workshop-sessions/{sessionId}/state-transitions", "session:transition:" + sessionId,
                request, SessionResponse.class, () -> lifecycleService.transitionState(authHeader, correlationId, sessionId, request));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/workshop-sessions/{sessionId}/staff-assignments")
    public ResponseEntity<SessionResponse> assignStaff(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody StaffAssignmentRequest request) {
        SessionResponse response = restIdempotency.execute(idempotencyKey, "REST:PUT:/api/v1/workshop-sessions/{sessionId}/staff-assignments", "session:staff:" + sessionId,
                request, SessionResponse.class, () -> responsibilityService.assignStaff(authHeader, correlationId, sessionId, request));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/workshop-sessions/{sessionId}/staff-assignments")
    public ResponseEntity<Void> removeStaff(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestParam("accountId") Long accountId) {
        restIdempotency.execute(idempotencyKey, "REST:DELETE:/api/v1/workshop-sessions/{sessionId}/staff-assignments", "session:staff-remove:" + sessionId + ":" + accountId,
                accountId, Void.class, () -> { responsibilityService.removeStaff(authHeader, correlationId, sessionId, accountId); return null; });
        return ResponseEntity.noContent().build();
    }

}
