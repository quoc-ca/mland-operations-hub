package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T041: Request DTO for session attendance finalization.
 * <p>
 * OpenAPI Contract:
 * - Path: /workshop-sessions/{sessionId}/attendance-finalizations (POST)
 * - Responses: 200 (Session), 403 (Problem), 409 (Problem)
 * <p>
 * Requirements (FR-006, FR-007):
 * - Applied to the whole session in one action while session is IN_PROGRESS.
 * - Allowed only to the assigned Staff member or recorded Manager operational lead.
 * - Carries explicit confirmation that the session has actually ended onsite.
 * - Assigns NO_SHOW only to unchecked tickets of that session.
 * <p>
 * Strict Boundaries:
 * - Contains NO customer PII, NO booking contact info, NO raw QR tokens, and NO internal entities.
 * - Does not accept legacy branch or personnel fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceFinalizationRequest {

    /**
     * Explicit confirmation that the session has actually ended onsite (FR-006).
     * Defaults to true when request is submitted.
     */
    @Builder.Default
    private Boolean confirmedSessionEnded = Boolean.TRUE;

    /**
     * Optional operator note recorded in the audit trail.
     */
    private String note;
}
