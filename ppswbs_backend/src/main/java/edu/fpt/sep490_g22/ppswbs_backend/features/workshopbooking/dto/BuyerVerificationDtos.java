package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request and response bodies of the buyer-verification routes (003.1 FR-041, decision 2026-11-13). */
public final class BuyerVerificationDtos {

    private BuyerVerificationDtos() {
    }

    /** Exactly one of contactEmail or contactPhone. */
    public record BuyerVerificationRequest(
            @Email @Size(max = 254) String contactEmail,
            @Size(min = 7, max = 32) String contactPhone
    ) {
    }

    public record BuyerVerificationChallenge(String verificationId, Instant expiresAt) {
    }

    public record BuyerVerificationConfirmation(@NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp) {
    }

    public record BuyerVerificationResult(String buyerVerificationReference, Instant expiresAt) {
    }
}
