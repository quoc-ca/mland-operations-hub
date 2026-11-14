package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EngravingItemDto {

    private String id;

    @Builder.Default
    private String type = "TEXT"; // TEXT, SYMBOL

    @Size(max = 100)
    private String content;

    @Builder.Default
    private String placement = "INNER_BAND"; // INNER_BAND, OUTER_BAND
}

