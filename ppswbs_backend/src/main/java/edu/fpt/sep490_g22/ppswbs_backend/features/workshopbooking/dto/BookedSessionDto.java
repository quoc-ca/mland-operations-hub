package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookedSessionDto {
    private UUID sessionReference;
    private Long locationId;
    private Integer ordinal;
    private Instant scheduledStart;
    private Instant scheduledEnd;
}
