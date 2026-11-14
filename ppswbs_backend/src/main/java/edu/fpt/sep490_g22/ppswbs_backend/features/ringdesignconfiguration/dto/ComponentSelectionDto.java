package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComponentSelectionDto {

    @NotBlank
    @Size(min = 1, max = 128)
    private String reference;

    @Min(1)
    @Max(64)
    private Integer quantity;

    @Size(max = 64)
    private String variant;

    @Size(max = 100)
    private String placement;
}

