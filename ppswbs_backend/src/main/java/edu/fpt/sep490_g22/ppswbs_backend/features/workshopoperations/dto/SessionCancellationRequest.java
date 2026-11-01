package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * T041: Request DTO for Manager session cancellation request.
 * <p>
 * OpenAPI Contract:
 * - Path: /workshop-sessions/{sessionId}/cancellation-requests (POST)
 * - Schema: ReasonedRequest
 * - Properties: reasonCode, reasonNote, evidenceReference
 * <p>
 * Requirements (FR-007a, FR-009, FR-009a):
 * - reasonCode is required and must belong to the standardized system catalogue.
 * - For OTHER_REQUIRES_NOTE, a descriptive note or evidence reference is required.
 * - Stops normal operations / check-in.
 * - If no commitments exist: cancels directly to terminal CANCELLED (HTTP 200).
 * - If commitments exist: stops operations and creates a 003.3 cancellation handoff (HTTP 202).
 * <p>
 * Strict Boundaries:
 * - No PII, customer contact info, tokens, or audit internals.
 * - Does not accept legacy branch or personnel fields.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class SessionCancellationRequest extends ReasonedRequest {

    public SessionCancellationRequest() {
        super();
    }

    public SessionCancellationRequest(String reasonCode, String reasonNote, String evidenceReference) {
        super(reasonCode, reasonNote, evidenceReference);
    }
}
