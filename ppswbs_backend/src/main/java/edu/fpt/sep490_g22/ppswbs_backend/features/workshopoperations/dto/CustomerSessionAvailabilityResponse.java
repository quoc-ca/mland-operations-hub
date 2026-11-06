package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CustomerSessionAvailabilityResponse(
        @NotNull UUID sessionReference,
        @NotNull Long locationId,
        @NotNull Instant scheduledStart,
        @NotNull Instant scheduledEnd,
        @NotNull String saleState,
        @NotNull Boolean customerSelectable,
        @NotNull @Min(0) Integer remainingCapacity
) {}
