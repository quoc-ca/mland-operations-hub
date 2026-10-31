package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkGenerationResponse {

    @Builder.Default
    private List<String> createdSessionIds = new ArrayList<>();

    @Builder.Default
    private List<String> skippedDuplicates = new ArrayList<>();
}
