package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EngravingInputDto {

    @NotNull
    private Boolean enabled;

    @Size(max = 200)
    private String text;

    @Size(max = 100)
    private String placement;

    @Builder.Default
    private List<EngravingItemDto> items = new ArrayList<>();

    @Builder.Default
    private List<CustomEngravingImageDto> customImages = new ArrayList<>();
}
