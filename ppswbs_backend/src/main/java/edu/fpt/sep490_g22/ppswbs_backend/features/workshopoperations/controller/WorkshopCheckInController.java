package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInOverrideRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.Ticket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.TicketResendRequest;
import edu.fpt.sep490_g22.ppswbs_backend.common.booking.BookingTicketRedeliveryPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CheckInService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CheckInOverrideService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.OperationsDeniedAttemptRecorder;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketResendService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * T031: REST controller for normal check-in and candidate booking lookup.
 * Corresponds to OpenAPI paths:
 * - POST /api/v1/workshop-sessions/{sessionId}/check-ins
 * - GET  /api/v1/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkshopCheckInController {

    private final CheckInService checkInService;
    private final TicketResendService ticketResendService;
    private final CheckInOverrideService checkInOverrideService;
    private final OperationsDeniedAttemptRecorder deniedAttemptRecorder;

    @PostMapping("/workshop-sessions/{sessionId}/check-ins")
    public ResponseEntity<CheckInResult> checkIn(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody CheckInRequest request) {
        CheckInResult result = checkInService.checkIn(authHeader, sessionId, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}")
    public ResponseEntity<List<Ticket>> lookupBookingTickets(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("sessionId") String sessionId,
            @PathVariable("bookingCode") String bookingCode) {
        List<Ticket> tickets = checkInService.lookupTicketsByBookingCode(authHeader, sessionId, bookingCode);
        return ResponseEntity.ok(tickets);
    }

    /** T116: Manager-only audited cross-session booking lookup (FR-003b). */
    @GetMapping("/workshop-booking-lookups/{bookingCode}")
    public ResponseEntity<edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.ManagerBookingLookup> managerBookingLookup(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationReference,
            @PathVariable("bookingCode") String bookingCode) {
        return ResponseEntity.ok(deniedAttemptRecorder.guard("MANAGER_BOOKING_LOOKUP", null, authHeader, correlationReference,
                () -> checkInService.managerBookingLookup(authHeader, correlationReference, bookingCode)));
    }

    @GetMapping("/workshop-sessions/{sessionId}/attendance-roster")
    public ResponseEntity<List<edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AttendanceRosterEntry>> attendanceRoster(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("sessionId") String sessionId) {
        return ResponseEntity.ok(checkInService.attendanceRoster(authHeader, sessionId));
    }

    /** T039: request delivery-safe ticket redelivery through Workshop Booking. */
    @PostMapping("/workshop-sessions/{sessionId}/ticket-resends")
    public ResponseEntity<Void> requestTicketResend(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationReference,
            @RequestHeader(value = "Idempotency-Key", required = false) String operationReference,
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody TicketResendRequest request) {
        deniedAttemptRecorder.guardVoid("TICKET_REDELIVERY_REQUESTED", sessionId, authHeader, correlationReference,
                () -> ticketResendService.requestResend(
                authHeader, correlationReference, operationReference, sessionId, request));
        return ResponseEntity.accepted().build();
    }

    /** T039: manually recover an eligible ticket with immutable reason evidence. */
    @PostMapping("/workshop-sessions/{sessionId}/check-in-overrides")
    public ResponseEntity<CheckInResult> overrideCheckIn(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationReference,
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody CheckInOverrideRequest request) {
        return ResponseEntity.ok(deniedAttemptRecorder.guard("TICKET_CHECK_IN_OVERRIDE", sessionId, authHeader, correlationReference,
                () -> checkInOverrideService.overrideCheckIn(
                authHeader, correlationReference, sessionId, request)));
    }
}
