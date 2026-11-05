package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.PendingHoldReleaseRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingPendingHoldReleaseService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingRecoveryCommandResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BookingPendingHoldReleaseController {

    private final BookingPendingHoldReleaseService releaseService;
    private final ObjectMapper mapper;

    @PostMapping("/api/v1/workshop-bookings/{bookingCode}/pending-hold-release")
    public ResponseEntity<?> release(
            @PathVariable String bookingCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody PendingHoldReleaseRequest request) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new BookingValidationException("Idempotency-Key must be between 16 and 128 characters");
        }
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        if (correlation.length() > 64) {
            throw new BookingValidationException("X-Correlation-Id must not exceed 64 characters");
        }

        BookingRecoveryCommandResult result = releaseService.release(
                authorization, bookingCode, idempotencyKey.trim(), correlation, request);
        if (result.statusCode() == 204) return ResponseEntity.noContent().build();
        try {
            return ResponseEntity.status(result.statusCode())
                    .body(mapper.readValue(result.responsePayload(), ProblemDetailResponse.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored pending-hold release response could not be read", ex);
        }
    }
}
