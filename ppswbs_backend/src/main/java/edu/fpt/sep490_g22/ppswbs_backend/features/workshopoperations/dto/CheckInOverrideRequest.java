package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * T034: Request DTO for authorized check-in override.
 * <p>
 * OpenAPI Contract:
 * - Path: /workshop-sessions/{sessionId}/check-in-overrides (POST)
 * - Schema: CheckInOverrideRequest
 * - Composition: allOf [ { $ref: '#/components/schemas/CheckInRequest' }, { $ref: '#/components/schemas/ReasonedRequest' } ]
 * - Properties: qrToken, ticketCode, reasonCode, reasonNote, evidenceReference
 * <p>
 * Requirements (FR-004, FR-007a):
 * - Exactly one of qrToken or ticketCode must be provided.
 * - Standardized reasonCode is required.
 * - For TICKET_REDELIVERY_UNAVAILABLE: short reasonNote and evidenceReference (failed-redelivery correlation reference) are required.
 * - For OTHER_REQUIRES_NOTE: a descriptive note or evidenceReference is required.
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
public class CheckInOverrideRequest extends ReasonedRequest {

    private String qrToken;
    private String ticketCode;

    @JsonIgnore
    @AssertTrue(message = "Exactly one of qrToken or ticketCode must be provided")
    public boolean isOneOfProvided() {
        boolean hasQrToken = qrToken != null && !qrToken.isBlank();
        boolean hasTicketCode = ticketCode != null && !ticketCode.isBlank();
        return hasQrToken ^ hasTicketCode;
    }
}
