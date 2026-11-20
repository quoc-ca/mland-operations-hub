package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public record WorkshopLocationSnapshot(
    Long id,
    String code,
    String displayName,
    boolean active
) {}
