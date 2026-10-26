package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
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

    @PatchMapping("/workshop-slots/{slotId}")
    public ResponseEntity<SlotResponse> updateSlot(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("slotId") String slotId,
            @Valid @RequestBody SlotRequest request) {
        SlotResponse response = slotService.updateSlot(authHeader, slotId, request);
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
            @Valid @RequestBody SessionRequest request) {
        SessionResponse response = sessionService.createSession(authHeader, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/workshop-sessions/bulk")
    public ResponseEntity<BulkGenerationResponse> generateBulkSessions(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody BulkSessionRequest request) {
        BulkGenerationResponse response = sessionService.generateBulkSessions(authHeader, request);
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
            @RequestBody SessionRequest request) {
        SessionResponse response = sessionService.updateSession(authHeader, sessionId, request);
        return ResponseEntity.ok(response);
    }
}
