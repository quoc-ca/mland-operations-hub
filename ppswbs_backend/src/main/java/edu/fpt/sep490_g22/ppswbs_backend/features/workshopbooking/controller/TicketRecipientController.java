package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BuyerVerificationDtos.BuyerVerificationChallenge;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BuyerVerificationDtos.BuyerVerificationConfirmation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BuyerVerificationDtos.BuyerVerificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BuyerVerificationDtos.BuyerVerificationResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.TicketRecipientRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.TicketRecipientResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.UUID;

/** Buyer verification and ticket recipient routes (003.1 FR-041 / T095, decision 2026-11-13). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TicketRecipientController {

    private static final String COMMAND_ACTION = "CHANGE_TICKET_RECIPIENT";

    private final BuyerVerificationService buyerVerification;
    private final TicketRecipientService recipients;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    @PostMapping("/workshop-booking-groups/{groupCode}/buyer-verifications")
    public ResponseEntity<BuyerVerificationChallenge> requestVerification(
            @PathVariable String groupCode,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody BuyerVerificationRequest request) {
        BuyerVerificationService.Challenge challenge = buyerVerification.requestChallenge(groupCode,
                request.contactEmail(), request.contactPhone(), correlation(correlationId));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BuyerVerificationChallenge(challenge.verificationId(), challenge.expiresAt()));
    }

    @PostMapping("/workshop-booking-groups/{groupCode}/buyer-verifications/{verificationId}/confirmations")
    public ResponseEntity<BuyerVerificationResult> confirmVerification(
            @PathVariable String groupCode,
            @PathVariable String verificationId,
            @Valid @RequestBody BuyerVerificationConfirmation request) {
        BuyerVerificationService.ConfirmResult result = buyerVerification.confirm(groupCode, verificationId, request.otp());
        if (result.errorCode() != null) {
            HttpStatus status = "BUYER_VERIFICATION_FAILED".equals(result.errorCode())
                    ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
            throw new WorkshopBookingProblemException(status, result.errorCode(), "Buyer verification was not accepted");
        }
        return ResponseEntity.ok(new BuyerVerificationResult(result.reference().buyerVerificationReference(),
                result.reference().expiresAt()));
    }

    @PutMapping("/workshop-tickets/{ticketCode}/recipient")
    public ResponseEntity<TicketRecipientResponse> changeRecipient(
            @PathVariable String ticketCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody TicketRecipientRequest request) {
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new BookingValidationException("Idempotency-Key must contain 16 to 128 characters");
        }
        String correlation = correlation(correlationId);
        // Authenticate any bearer before a cached response may be returned.
        TicketRecipientService.Actor actor = recipients.resolveActor(authorization);
        String semantic = ticketCode + "|" + request.recipientEmail().trim().toLowerCase(Locale.ROOT) + "|"
                + (request.buyerVerificationReference() == null ? "" : request.buyerVerificationReference()) + "|"
                + (request.reason() == null ? "" : request.reason().trim()) + "|"
                + (actor.anonymous() ? "anonymous" : actor.account().accountId());
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey, COMMAND_ACTION, semantic, correlation);
        if (claim.cachedOutcome()) {
            try {
                return ResponseEntity.status(claim.statusCode())
                        .body(mapper.readValue(claim.responsePayload(), TicketRecipientResponse.class));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("The stored recipient response could not be read", ex);
            }
        }
        return ResponseEntity.ok(recipients.change(ticketCode, request.recipientEmail(),
                request.buyerVerificationReference(), request.reason(), actor, idempotencyKey, correlation, claim));
    }

    private static String correlation(String correlationId) {
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId.trim();
    }
}
