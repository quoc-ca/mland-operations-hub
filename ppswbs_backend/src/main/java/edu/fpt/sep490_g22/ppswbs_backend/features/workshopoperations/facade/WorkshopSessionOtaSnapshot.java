package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record WorkshopSessionOtaSnapshot(
        @JsonProperty("s")
        String sessionReference,
        @JsonProperty("l")
        String locationReference,
        @JsonProperty("a")
        Instant scheduledStart,
        @JsonProperty("b")
        Instant scheduledEnd,
        @JsonProperty("c")
        int sellableAvailability,
        @JsonProperty("q")
        String saleState,
        @JsonProperty("v")
        long sourceVersion,
        @JsonProperty("x")
        String changeReference,
        @JsonProperty("e")
        Instant effectiveAt) {
}
