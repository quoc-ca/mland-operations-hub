package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public class PublishRequestDto {

    @NotBlank
    @Size(min = 1, max = 64)
    private String expectedActiveRulesVersion;

    @NotNull
    @Valid
    private PolicyValuesDto candidate;

    // Checked manually in service/controller to return specific PREVIEW_REQUIRED code
    private String previewReference;
}
