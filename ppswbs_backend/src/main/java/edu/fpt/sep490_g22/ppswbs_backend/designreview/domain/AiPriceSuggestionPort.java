package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

public interface AiPriceSuggestionPort {

    record PriceSuggestionResult(
            boolean success,
            Double suggestedPrice,
            String explanation,
            String providerCorrelationId,
            String failureCode
    ) {}

    PriceSuggestionResult suggestPrice(String proposalId, TargetType targetType, String pricingContextJson, String correlationId);
}
