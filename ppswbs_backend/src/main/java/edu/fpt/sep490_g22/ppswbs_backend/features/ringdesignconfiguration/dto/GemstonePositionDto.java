package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GemstonePositionDto {
    private String id;
    private String reference;
    private Integer index;
    private Double angleDeg;
}
