package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

public interface AiTextSupportPort {

    record TextSupportResult(
            String responseText,
            AiScope scope,
            String providerCorrelationId,
            boolean success,
            String failureCode
    ) {}

    TextSupportResult generateSupportResponse(String promptText, String correlationId);
}
