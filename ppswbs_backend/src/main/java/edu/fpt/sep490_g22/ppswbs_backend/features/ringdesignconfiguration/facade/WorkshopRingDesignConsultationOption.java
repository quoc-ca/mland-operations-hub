package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.facade;

public record WorkshopRingDesignConsultationOption(
    String designReference,
    String displayCode,
    String displayName,
    Integer designVersion,
    String previewImageUrl,
    String previewSummary
) {}
