package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingCreationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyClaim;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workshop-bookings")
@RequiredArgsConstructor
public class WorkshopBookingCreationController {
    private final BookingCreationService creationService;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    @PostMapping
    public ResponseEntity<BookingSummaryResponse> createGuest(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody CreateBookingRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingValidationException(
                    "Idempotency-Key must contain 1 to 128 characters");
        }
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId;
        String semanticContent = semanticContent(request);
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey, "CREATE_GUEST_BOOKING", semanticContent, correlation);
        if (claim.cachedOutcome()) {
            try {
                return ResponseEntity.status(claim.statusCode()).body(mapper.readValue(claim.responsePayload(), BookingSummaryResponse.class));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Stored booking response could not be read", ex);
            }
        }
        BookingSummaryResponse response = creationService.createGuest(request, correlation, claim);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String semanticContent(CreateBookingRequest request) {
        return request.getPackageId() + "|" + request.getParticipantCount() + "|"
                + request.getSessionReferences().stream().sorted(Comparator.comparing(UUID::toString)).toList() + "|"
                + request.getContactEmail().trim().toLowerCase(java.util.Locale.ROOT) + "|"
                + request.getContactPhone().trim();
    }
}
