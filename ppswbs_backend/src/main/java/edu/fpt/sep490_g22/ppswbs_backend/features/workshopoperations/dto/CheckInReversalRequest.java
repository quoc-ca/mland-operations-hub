package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * T041: Request DTO for Manager-only reversal of an erroneous check-in before attendance finalization.
 * <p>
 * OpenAPI Contract:
 * - Path: /workshop-sessions/{sessionId}/check-in-reversals (POST)
 * - Schema: CheckInReversalRequest
 * - Composition: allOf [ { $ref: '#/components/schemas/ReasonedRequest' }, { type: object, required: [ticketCode], properties: { ticketCode: { type: string } } } ]
 * - Properties: ticketCode, reasonCode, reasonNote, evidenceReference
 * <p>
 * Requirements (FR-007, FR-007a):
 * - ticketCode is required.
 * - Standardized reasonCode is required.
 * - For OTHER_REQUIRES_NOTE: descriptive reasonNote or evidenceReference is required.
 * <p>
 * Strict Boundaries:
 * - No PII, customer email/phone, raw QR payloads, or internal audit metadata.
 * - Does not accept legacy branch or personnel fields.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInReversalRequest extends ReasonedRequest {

    @NotBlank(message = "ticketCode is required")
    @Size(max = 64, message = "ticketCode must not exceed 64 characters")
    private String ticketCode;
}
