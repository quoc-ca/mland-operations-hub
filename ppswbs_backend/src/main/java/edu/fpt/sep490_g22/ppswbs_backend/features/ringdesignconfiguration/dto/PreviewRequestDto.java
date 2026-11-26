package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public class PreviewRequestDto {

    @NotBlank
    @Size(min = 1, max = 64)
    private String expectedActiveRulesVersion;

    @NotNull
    @Valid
    private PolicyValuesDto candidate;

    @Size(max = 20, message = "Tối đa 20 mẫu thử nghiệm")
    @Valid
    private List<SampleSelectionDto> samples;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class SampleSelectionDto {
        @NotBlank
        @Size(min = 1, max = 80)
        private String sampleName;

        @NotBlank
        @Size(min = 1, max = 128)
        private String materialReference;

        @NotBlank
        @Size(min = 1, max = 128)
        private String shapeReference;

        @Valid
        private List<SelectionQuantityDto> gemstones;

        @Valid
        private List<SelectionQuantityDto> attachments;

        @Builder.Default
        private Boolean engravingEnabled = false;

        @Min(0)
        @Max(2)
        @Builder.Default
        private Integer customImageCount = 0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class SelectionQuantityDto {
        @NotBlank
        @Size(min = 1, max = 128)
        private String reference;

        @NotNull
        @Min(1)
        private Integer quantity;
    }
}
