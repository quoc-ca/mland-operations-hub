package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDesignRequest {

    @NotBlank
    @Size(min = 1, max = 128)
    private String packageReference;

    @Size(min = 1, max = 128)
    private String sourceModelReference;

    @NotBlank
    @Pattern(regexp = "PUBLISHED_MODEL|BLANK")
    private String startMode;
}

