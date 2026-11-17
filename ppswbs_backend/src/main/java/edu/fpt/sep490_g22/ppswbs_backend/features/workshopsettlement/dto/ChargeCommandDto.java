package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Either a catalogue add-on (the price is decided by the backend) or a variable charge (description, amount and reason).
 * Both need the operator's "customer agreed" confirmation; the backend generates and stores the consent reference.
 */
public record ChargeCommandDto(String catalogueAddOnReference, @Email String contactEmail,
                               String contactPhone, @Size(max = 200) String description,
                               @Positive Long amount, @Size(max = 255) String reason,
                               Boolean customerConsentConfirmed) {
    public ChargeCommandDto(String catalogueAddOnReference, String contactEmail, String contactPhone,
                            String description, Long amount, String reason) {
        this(catalogueAddOnReference, contactEmail, contactPhone, description, amount, reason, null);
    }

    @AssertTrue(message = "supply either a staff-confirmed catalogue add-on, or a staff variable charge")
    public boolean hasValidCommandShape() {
        boolean addOn = catalogueAddOnReference != null && !catalogueAddOnReference.isBlank();
        boolean variable = description != null && !description.isBlank() && amount != null && amount > 0
                && reason != null && !reason.isBlank();
        if (addOn) return !variable && contactEmail == null && contactPhone == null;
        return variable && contactEmail == null && contactPhone == null;
    }

    public boolean consentConfirmed() {
        return Boolean.TRUE.equals(customerConsentConfirmed);
    }
}
