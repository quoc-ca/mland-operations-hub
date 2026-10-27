package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionAvailabilityResponse {
    private Long sessionId;
    private Long locationId;
    private Instant scheduledStart;
    private Instant scheduledEnd;
    private String saleState;
    private Boolean customerSelectable;
    private Integer remainingCapacity;
}
