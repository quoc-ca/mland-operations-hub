package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;

public record GuestContactProofDto(@Email String contactEmail, String contactPhone) {
    @AssertTrue(message = "exactly one contact proof is required")
    public boolean hasExactlyOneContactProof() {
        return (contactEmail != null && !contactEmail.isBlank()) ^ (contactPhone != null && !contactPhone.isBlank());
    }
}
