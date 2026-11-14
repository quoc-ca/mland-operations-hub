package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

public interface ImageFeatureAnalysisPort {

    record FeatureAnalysisResult(
            boolean success,
            String schemaVersion,
            String imageQualityJson,
            String candidateFeaturesJson,
            String confidenceJson,
            String safeExplanation,
            Long estimatedValue,
            String providerCorrelationId,
            String failureCode
    ) {}

    FeatureAnalysisResult analyzeRingImage(byte[] imageBytes, String contentType, String catalogueReference, String correlationId);
}
