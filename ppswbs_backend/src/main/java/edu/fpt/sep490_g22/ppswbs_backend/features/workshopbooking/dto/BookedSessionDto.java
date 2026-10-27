package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookedSessionDto {
    private Long sessionId;
    private Long locationId;
    private Integer ordinal;
}
