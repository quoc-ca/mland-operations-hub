package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingGroupResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingGroupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingGroupCreationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyClaim;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Confirms a quoted cart into one booking group, holding every line all or none (003.1 T092, FR-031). */
@RestController
@RequestMapping("/api/v1/workshop-booking-groups")
@RequiredArgsConstructor
public class WorkshopBookingGroupController {

    private static final String COMMAND_ACTION = "CREATE_BOOKING_GROUP";

    private final BookingGroupCreationService creationService;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    @PostMapping
    public ResponseEntity<BookingGroupResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateBookingGroupRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BookingValidationException("Idempotency-Key must contain 1 to 128 characters");
        }
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId;
        // Authenticate the buyer before a cached response may be returned, as the single-booking route does.
        Long buyerMemberAccountId = creationService.requireBuyer(authorization, request.quoteRef());
        String semanticContent = request.quoteRef() + "|"
                + request.contactEmail().trim().toLowerCase(java.util.Locale.ROOT)
                + "|" + request.contactPhone().trim();
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey, COMMAND_ACTION, semanticContent,
                correlation);
        if (claim.cachedOutcome()) {
            return cached(claim);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creationService.create(request, buyerMemberAccountId, correlation, claim));
    }

    private ResponseEntity<BookingGroupResponse> cached(BookingIdempotencyClaim claim) {
        try {
            return ResponseEntity.status(claim.statusCode())
                    .body(mapper.readValue(claim.responsePayload(), BookingGroupResponse.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("The stored booking group response could not be read", ex);
        }
    }
}
