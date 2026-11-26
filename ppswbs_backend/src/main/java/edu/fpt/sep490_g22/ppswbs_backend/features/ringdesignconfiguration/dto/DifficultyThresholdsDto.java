package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public class DifficultyThresholdsDto {

    private static final String DECIMAL_REGEX = "^(0|[1-9][0-9]{0,5})(\\.[0-9]{1,4})?$";

    @NotBlank
    @Pattern(regexp = DECIMAL_REGEX, message = "easyMax không hợp lệ")
    private String easyMax;

    @NotBlank
    @Pattern(regexp = DECIMAL_REGEX, message = "mediumMax không hợp lệ")
    private String mediumMax;

    @NotBlank
    @Pattern(regexp = DECIMAL_REGEX, message = "hardMax không hợp lệ")
    private String hardMax;

    @NotBlank
    @Pattern(regexp = DECIMAL_REGEX, message = "veryHardMax không hợp lệ")
    private String veryHardMax;

    @NotBlank
    @Pattern(regexp = DECIMAL_REGEX, message = "waxAbove không hợp lệ")
    private String waxAbove;

    @NotNull
    @Min(0)
    @Max(9)
    private Integer silverClayGemstonesAbove;
}
