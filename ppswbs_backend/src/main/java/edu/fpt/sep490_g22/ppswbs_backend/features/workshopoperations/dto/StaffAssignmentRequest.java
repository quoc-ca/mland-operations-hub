package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T020: Request DTO for assigning a Staff member to a workshop session.
 * <p>
 * OpenAPI Contract:
 * - Path: /workshop-sessions/{sessionId}/staff-assignments (PUT)
 * - Required: accountId (int64)
 * <p>
 * Does not leak internal entities, Firebase tokens, PII, or audit metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffAssignmentRequest {

    @NotNull(message = "accountId is required")
    private Long accountId;
}
