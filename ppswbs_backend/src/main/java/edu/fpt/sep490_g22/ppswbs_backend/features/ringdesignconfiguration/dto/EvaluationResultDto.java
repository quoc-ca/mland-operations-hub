package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationResultDto {

    private String estimatedPrice;

    private String currency;

    private String difficultyScore;

    private String rulesVersion;

    @Builder.Default
    @JsonProperty("advisory")
    private boolean advisory = true;
}

