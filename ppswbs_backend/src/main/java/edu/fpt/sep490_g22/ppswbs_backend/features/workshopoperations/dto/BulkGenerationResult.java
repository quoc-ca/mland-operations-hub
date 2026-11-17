package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Direct OpenAPI schema alias for BulkGenerationResponse.
 */
@NoArgsConstructor
public class BulkGenerationResult extends BulkGenerationResponse {

    public BulkGenerationResult(List<String> createdSessionIds, List<String> skippedDuplicates) {
        super(createdSessionIds, skippedDuplicates);
    }
}
