package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveDesignVersionRequest {

    @NotBlank
    @Size(min = 1, max = 128)
    private String packageReference;

    @NotBlank
    @Size(min = 1, max = 128)
    private String materialReference;

    @NotBlank
    @Size(min = 1, max = 128)
    private String shapeReference;

    @NotBlank
    @Size(min = 1, max = 64)
    private String ringSize;

    @DecimalMin("0.5")
    @DecimalMax("10.0")
    private BigDecimal thicknessMm;

    @DecimalMin("0.5")
    @DecimalMax("20.0")
    private BigDecimal bandWidthMm;

    @NotNull
    @Size(max = 64)
    @Valid
    @Builder.Default
    private List<ComponentSelectionDto> components = new ArrayList<>();

    @NotNull
    @Size(max = 64)
    @Valid
    @Builder.Default
    private List<AttachmentSelectionDto> attachments = new ArrayList<>();

    @NotNull
    @Valid
    private EngravingInputDto engraving;
}

