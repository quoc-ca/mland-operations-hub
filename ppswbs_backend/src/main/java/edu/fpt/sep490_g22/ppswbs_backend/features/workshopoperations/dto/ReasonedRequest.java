package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * T034: Base request DTO representing the OpenAPI 'ReasonedRequest' schema.
 * Used for check-in overrides, attendance corrections, and session cancellations.
 * <p>
 * OpenAPI Contract:
 * - Schema: ReasonedRequest
 * - Required: [reasonCode]
 * - Properties:
 *     reasonCode: string
 *     reasonNote: string
 *     evidenceReference: string
 * <p>
 * Strict Boundaries:
 * - Does not accept legacy branch/personnel fields.
 * - Enforces standardized reason code validation and note/evidence requirements (FR-004, FR-007a).
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ReasonedRequest {

    @NotBlank(message = "reasonCode is required")
    @Size(max = 80, message = "reasonCode must not exceed 80 characters")
    private String reasonCode;

    @Size(max = 500, message = "reasonNote must not exceed 500 characters")
    private String reasonNote;

    @Size(max = 255, message = "evidenceReference must not exceed 255 characters")
    private String evidenceReference;

    @JsonIgnore
    @AssertTrue(message = "reasonCode must belong to the standardized system catalogue")
    public boolean isReasonCodeRecognized() {
        if (reasonCode == null || reasonCode.isBlank()) {
            return true; // handled by @NotBlank
        }
        return StandardizedReasonCode.isRecognized(reasonCode);
    }

    @JsonIgnore
    @AssertTrue(message = "For reasonCode TICKET_REDELIVERY_UNAVAILABLE, reasonNote and evidenceReference (failed-redelivery correlation reference) are required")
    public boolean isTicketRedeliveryEvidenceValid() {
        if ("TICKET_REDELIVERY_UNAVAILABLE".equalsIgnoreCase(reasonCode)) {
            return reasonNote != null && !reasonNote.isBlank()
                    && evidenceReference != null && !evidenceReference.isBlank();
        }
        return true;
    }

    @JsonIgnore
    @AssertTrue(message = "For reasonCode OTHER_REQUIRES_NOTE, a descriptive reasonNote or evidenceReference is required")
    public boolean isOtherRequiresNoteValid() {
        if ("OTHER_REQUIRES_NOTE".equalsIgnoreCase(reasonCode)) {
            return (reasonNote != null && !reasonNote.isBlank())
                    || (evidenceReference != null && !evidenceReference.isBlank());
        }
        return true;
    }
}
