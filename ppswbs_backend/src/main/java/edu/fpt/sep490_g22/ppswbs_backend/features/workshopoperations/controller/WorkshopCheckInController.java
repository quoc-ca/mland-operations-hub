package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.Ticket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CheckInService;
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
}
