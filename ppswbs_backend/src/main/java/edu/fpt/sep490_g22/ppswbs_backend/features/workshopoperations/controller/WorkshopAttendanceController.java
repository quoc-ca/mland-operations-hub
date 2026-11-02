package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AttendanceCorrectionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AttendanceFinalizationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInReversalRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionCancellationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.Ticket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceCorrectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceFinalizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CancellationHandoffService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CancellationHandoffService.CancellationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T046: REST controller for whole-session attendance closure, check-in reversal,
 * post-finalization correction, and reasoned session cancellation.
 * <p>
 * Corresponds to OpenAPI paths:
 * - POST /api/v1/workshop-sessions/{sessionId}/attendance-finalizations
 * - POST /api/v1/workshop-sessions/{sessionId}/check-in-reversals
 * - POST /api/v1/workshop-sessions/{sessionId}/attendance-corrections
 * - POST /api/v1/workshop-sessions/{sessionId}/cancellation-requests
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkshopAttendanceController {

    private final AttendanceFinalizationService finalizationService;
    private final AttendanceCorrectionService correctionService;
    private final CancellationHandoffService cancellationHandoffService;

    /**
     * Finalises the whole session's attendance.
     * Accessible only to active assigned Staff or operational lead during IN_PROGRESS.
     */
    @PostMapping("/workshop-sessions/{sessionId}/attendance-finalizations")
    public ResponseEntity<SessionResponse> finalizeAttendance(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody(required = false) AttendanceFinalizationRequest request) {
        log.info("REST: finalize attendance requested for session {}", sessionId);
        AttendanceFinalizationRequest effectiveRequest = (request != null)
                ? request
                : AttendanceFinalizationRequest.builder().confirmedSessionEnded(Boolean.TRUE).build();

        SessionResponse response = finalizationService.finalizeAttendance(authHeader, correlationId, sessionId, effectiveRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Reverses an erroneous check-in before attendance finalization.
     * Strictly Manager-only within location scope.
     */
    @PostMapping("/workshop-sessions/{sessionId}/check-in-reversals")
    public ResponseEntity<Ticket> reverseCheckIn(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody CheckInReversalRequest request) {
        log.info("REST: check-in reversal requested for ticket {} at session {}",
                request != null ? request.getTicketCode() : "null", sessionId);
        Ticket response = correctionService.reverseCheckIn(authHeader, correlationId, sessionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Corrects attendance after attendance finalization.
     * Strictly Manager-only within location scope; target state restricted to CHECKED_IN or NO_SHOW.
     */
    @PostMapping("/workshop-sessions/{sessionId}/attendance-corrections")
    public ResponseEntity<Ticket> correctFinalizedAttendance(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody AttendanceCorrectionRequest request) {
        log.info("REST: attendance correction requested for ticket {} to state {} at session {}",
                request != null ? request.getTicketCode() : "null",
                request != null ? request.getCorrectedAttendanceState() : "null",
                sessionId);
        Ticket response = correctionService.correctFinalizedAttendance(authHeader, correlationId, sessionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Requests session cancellation with standardized reason code and audit note.
     * Returns 200 CANCELLED if no commitments exist, or 202 Accepted if a settlement handoff is opened.
     */
    @PostMapping("/workshop-sessions/{sessionId}/cancellation-requests")
    public ResponseEntity<SessionResponse> requestSessionCancellation(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Operation-Reference", required = false) String operationReference,
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody SessionCancellationRequest request) {
        log.info("REST: session cancellation requested for session {} with reason {}",
                sessionId, request != null ? request.getReasonCode() : "null");
        CancellationResult result = cancellationHandoffService.requestSessionCancellation(
                authHeader, operationReference, correlationId, sessionId, request
        );

        if (result.getStatusCode() == 202) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }
        return ResponseEntity.ok(result.getSession());
    }
}
