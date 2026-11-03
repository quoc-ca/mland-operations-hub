package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ChargeCommandDto(String catalogueAddOnReference, @Email String contactEmail,
                               String contactPhone, @Size(max = 200) String description,
                               @Positive Long amount, @Size(max = 255) String reason) {
    @AssertTrue(message = "supply either a catalogue add-on and optional single guest proof, or a staff variable charge")
    public boolean hasValidCommandShape() {
        boolean addOn = catalogueAddOnReference != null && !catalogueAddOnReference.isBlank();
        boolean guestProof = (contactEmail != null && !contactEmail.isBlank()) ^ (contactPhone != null && !contactPhone.isBlank());
        boolean variable = description != null && !description.isBlank() && amount != null && amount > 0
                && reason != null && !reason.isBlank();
        if (addOn) return !variable && !(contactEmail != null && !contactEmail.isBlank() && contactPhone != null && !contactPhone.isBlank());
        return !guestProof && variable && contactEmail == null && contactPhone == null;
    }
}
