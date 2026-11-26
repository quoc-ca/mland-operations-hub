package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.DifficultyLevel;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RecommendationCode;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationResultDto {

    @JsonIgnore
    private Long policyVersionId;

    private String estimatedPrice;

    private String currency;

    private String difficultyScore;

    private DifficultyLevel difficultyLevel;

    private RecommendationCode recommendationCode;

    private String recommendedPackageReference;

    private String materialDisclosure;

    private String recommendationReason;

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
