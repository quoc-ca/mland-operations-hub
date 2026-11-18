package edu.fpt.sep490_g22.ppswbs_backend.designreview.facade;

public record DesignRequestConsultationOption(
    String requestReference,
    String displayCode,
    String displayName,
    Integer requestVersion,
    String previewImageUrl,
    String previewSummary
) {}
