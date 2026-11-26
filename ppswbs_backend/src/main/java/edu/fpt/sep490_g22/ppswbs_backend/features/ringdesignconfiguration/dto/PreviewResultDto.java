package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.DifficultyLevel;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RecommendationCode;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewResultDto {

    private String activeRulesVersion;
    private String catalogueRevision;
    private boolean publicationEligible;
    private int blockingModelCount;
    private String previewReference;
    private Instant previewExpiresAt;

    @Builder.Default
    private List<PreviewComparisonDto> comparisons = new ArrayList<>();

    @Builder.Default
    private List<UnavailableStandardCaseDto> unavailableStandardCases = new ArrayList<>();

    @Builder.Default
    private List<ModelImpactDto> modelImpacts = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SampleOutcomeDto {
        private String sampleName;
        private String difficultyScore;
        private DifficultyLevel difficultyLevel;
        private RecommendationCode recommendationCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PreviewComparisonDto {
        private String source; // STANDARD | ADMIN
        private SampleOutcomeDto before;
        private SampleOutcomeDto after;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UnavailableStandardCaseDto {
        private String sampleName;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ModelImpactDto {
        private String modelReference;
        private String name;
        private String materialReference;
        private SampleOutcomeDto before;
        private SampleOutcomeDto after;
        private boolean eligible;

        @Builder.Default
        private List<String> blockingReasons = new ArrayList<>();
    }
}
