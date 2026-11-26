package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyVersionDto {
    private String rulesVersion;
    private String status; // EFFECTIVE | HISTORICAL
    private Instant publishedAt;
    private String publishedBy;
    private List<OptionScoreDto> optionScores;
    private String engravingBonus;
    private String customImageBonusPerImage;
    private DifficultyThresholdsDto thresholds;
}
