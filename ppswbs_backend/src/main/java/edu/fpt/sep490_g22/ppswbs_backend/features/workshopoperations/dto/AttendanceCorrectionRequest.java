package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * T041: Request DTO for Manager-only post-finalization attendance correction.
 * <p>
 * OpenAPI Contract:
 * - Path: /workshop-sessions/{sessionId}/attendance-corrections (POST)
 * - Schema: AttendanceCorrectionRequest
 * - Composition: allOf [ { $ref: '#/components/schemas/ReasonedRequest' }, { type: object, required: [ticketCode, correctedAttendanceState], properties: { ticketCode: { type: string }, correctedAttendanceState: { type: string, enum: [CHECKED_IN, NO_SHOW] } } } ]
 * - Properties: ticketCode, correctedAttendanceState, reasonCode, reasonNote, evidenceReference
 * <p>
 * Requirements (FR-007, FR-007a, FR-008):
 * - ticketCode is required.
 * - correctedAttendanceState is required and MUST be either CHECKED_IN or NO_SHOW (NOT_CHECKED_IN is rejected).
 * - Standardized reasonCode is required and validated against the system catalogue.
 * - Preserves before/after state and correction reason in the append-only audit trail.
 * <p>
 * Strict Boundaries:
 * - No PII, customer contact info, tokens, QR raw values, or internal audit metadata.
 * - Does not accept legacy branch or personnel fields.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCorrectionRequest extends ReasonedRequest {

    @NotBlank(message = "ticketCode is required")
    @Size(max = 64, message = "ticketCode must not exceed 64 characters")
    private String ticketCode;

    @NotNull(message = "correctedAttendanceState is required")
    private AttendanceState correctedAttendanceState;

    @JsonIgnore
    @AssertTrue(message = "correctedAttendanceState must be either CHECKED_IN or NO_SHOW")
    public boolean isCorrectedAttendanceStateAllowed() {
        if (correctedAttendanceState == null) {
            return true; // Handled by @NotNull
        }
        return correctedAttendanceState == AttendanceState.CHECKED_IN
                || correctedAttendanceState == AttendanceState.NO_SHOW;
    }
}
