package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomEngravingImageDto {

    private String id;

    @Builder.Default
    private String placement = "INNER_BAND"; // INNER_BAND, OUTER_BAND

    private String imageUrl; // data:image/... or public url

    private Integer cropX;

    private Integer cropY;

    @Builder.Default
    private Integer cropWidth = 160;

    @Builder.Default
    private Integer cropHeight = 160;

    @Builder.Default
    private BigDecimal price = new BigDecimal("100000.00");
}

