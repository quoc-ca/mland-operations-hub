package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T034: Request DTO for requesting an auditable resend of the buyer's tickets.
 * <p>
 * OpenAPI Contract:
 * - Path: /workshop-sessions/{sessionId}/ticket-resends (POST)
 * - Required: [bookingCode]
 * - Properties: bookingCode (string)
 * <p>
 * Strict Privacy & Security Boundaries:
 * - Does not leak buyer PII, contact info (email/phone), tokens, or JPA entities.
 * - Does not accept legacy branch or personnel fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResendRequest {

    @NotBlank(message = "bookingCode is required")
    private String bookingCode;
}
