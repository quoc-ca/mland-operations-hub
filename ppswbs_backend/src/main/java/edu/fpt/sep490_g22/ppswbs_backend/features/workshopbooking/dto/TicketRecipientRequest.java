package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code RecipientRequest} of the cart contract (003.1 FR-041, decision 2026-11-13). */
public record TicketRecipientRequest(
        @NotBlank @Email @Size(max = 254) String recipientEmail,
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String buyerVerificationReference,
        @Size(min = 1, max = 500) String reason
) {
}
