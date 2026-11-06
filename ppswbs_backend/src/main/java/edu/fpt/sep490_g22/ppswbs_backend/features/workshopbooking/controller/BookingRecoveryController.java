package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingActionOutcomeResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.MagicLinkIncidentRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.ConfirmationDeliveryRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.LatePaymentFollowUpRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingRecoveryCommandResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingRecoveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BookingRecoveryController {

    private final BookingRecoveryService recovery;
    private final ObjectMapper mapper;

    @PostMapping("/api/v1/workshop-bookings/{bookingCode}/confirmation-deliveries")
    public ResponseEntity<?> requestConfirmationDelivery(
            @PathVariable String bookingCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody(required = false) ConfirmationDeliveryRequest request) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new BookingValidationException("Idempotency-Key must be between 16 and 128 characters");
        }
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        if (correlation.length() > 64) {
            throw new BookingValidationException("X-Correlation-Id must not exceed 64 characters");
        }
        BookingRecoveryCommandResult result = recovery.requestConfirmationDelivery(authorization, bookingCode,
                idempotencyKey, correlation, request);
        try {
            Object response = result.statusCode() == 202
                    ? mapper.readValue(result.responsePayload(), BookingActionOutcomeResponse.class)
                    : mapper.readValue(result.responsePayload(), ProblemDetailResponse.class);
            return ResponseEntity.status(result.statusCode()).body(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored confirmation delivery response could not be read", ex);
        }
    }

    @PostMapping("/api/v1/workshop-bookings/{bookingCode}/magic-link-incidents")
    public ResponseEntity<?> handleMagicLinkIncident(
            @PathVariable String bookingCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody MagicLinkIncidentRequest request) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new BookingValidationException("Idempotency-Key must be between 16 and 128 characters");
        }
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        if (correlation.length() > 64) {
            throw new BookingValidationException("X-Correlation-Id must not exceed 64 characters");
        }
        BookingRecoveryCommandResult result = recovery.handleMagicLinkIncident(authorization, bookingCode,
                idempotencyKey, correlation, request);
        try {
            Object response = result.statusCode() == 200
                    ? mapper.readValue(result.responsePayload(), BookingActionOutcomeResponse.class)
                    : mapper.readValue(result.responsePayload(), ProblemDetailResponse.class);
            return ResponseEntity.status(result.statusCode()).body(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored recovery response could not be read", ex);
        }
    }

    @PostMapping("/api/v1/workshop-bookings/{bookingCode}/late-payment-follow-ups")
    public ResponseEntity<?> handleLatePaymentFollowup(
            @PathVariable String bookingCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody LatePaymentFollowUpRequest request) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new BookingValidationException("Idempotency-Key must be between 16 and 128 characters");
        }
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        if (correlation.length() > 64) throw new BookingValidationException("X-Correlation-Id must not exceed 64 characters");
        BookingRecoveryCommandResult result = recovery.handleLatePaymentFollowup(authorization, bookingCode,
                idempotencyKey, correlation, request);
        try {
            Object response = result.statusCode() == 200
                    ? mapper.readValue(result.responsePayload(), BookingActionOutcomeResponse.class)
                    : mapper.readValue(result.responsePayload(), ProblemDetailResponse.class);
            return ResponseEntity.status(result.statusCode()).body(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored late-payment follow-up response could not be read", ex);
        }
    }
}
