package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T020: Request DTO for taking or handing over the single Manager operational lead role.
 * <p>
 * OpenAPI Contract:
 * - Path: /workshop-sessions/{sessionId}/operational-lead (PUT)
 * - Required: managerAccountId (int64), reasonCode
 * - Optional: reasonNote, evidenceReference
 * <p>
 * Does not leak internal entities, Firebase tokens, PII, or audit metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalLeadRequest {

    @NotNull(message = "managerAccountId is required")
    private Long managerAccountId;

    @NotBlank(message = "reasonCode is required")
    private String reasonCode;

    private String reasonNote;

    private String evidenceReference;
}
