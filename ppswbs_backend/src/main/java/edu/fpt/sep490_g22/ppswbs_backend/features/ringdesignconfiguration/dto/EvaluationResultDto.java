package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationResultDto {

    private String estimatedPrice;

    private String currency;

    private String difficultyScore;

    private String rulesVersion;

    private String estimatedWeightGrams;

    private String baseWeightLimitGrams;

    private String materialCost;

    private String excessMaterialCost;

    private String engravingCost;

    private Integer customImageCount;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    @JsonProperty("advisory")
    private boolean advisory = true;
}
