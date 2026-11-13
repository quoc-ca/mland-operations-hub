package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.LatePaymentDecisionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.LatePaymentReviewResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyClaim;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingValidationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.LatePaymentReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Manager queue and decisions for late group payments (003.1 FR-040/FR-040a / T094). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LatePaymentReviewController {

    private static final String COMMAND_ACTION = "DECIDE_LATE_PAYMENT_REVIEW";

    private final LatePaymentReviewService reviews;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    @GetMapping("/late-payment-reviews")
    public List<LatePaymentReviewResponse> queue(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "status", required = false) String status) {
        return reviews.queue(authorization, status);
    }

    @PostMapping("/workshop-booking-groups/{groupCode}/late-payment-reviews/{reviewId}/decisions")
    public ResponseEntity<LatePaymentReviewResponse> decide(
            @PathVariable String groupCode,
            @PathVariable String reviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody LatePaymentDecisionRequest request) {
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new BookingValidationException("Idempotency-Key must contain 16 to 128 characters");
        }
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        AccountSummary manager = reviews.requireManager(authorization);
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey, COMMAND_ACTION,
                groupCode + "|" + reviewId + "|" + request.decision() + "|" + request.reason().trim() + "|"
                        + manager.accountId(), correlation);
        if (claim.cachedOutcome()) {
            try {
                return ResponseEntity.status(claim.statusCode())
                        .body(mapper.readValue(claim.responsePayload(), LatePaymentReviewResponse.class));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("The stored decision response could not be read", ex);
            }
        }
        return ResponseEntity.ok(reviews.decide(groupCode, reviewId, request.decision(), request.reason(), manager,
                correlation, claim));
    }
}
