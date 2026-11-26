package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyValuesDto {

    private static final String DECIMAL_REGEX = "^(0|[1-9][0-9]{0,5})(\\.[0-9]{1,4})?$";

    @NotEmpty(message = "optionScores không được rỗng")
    @Valid
    private List<OptionScoreDto> optionScores;

    @NotBlank
    @Pattern(regexp = DECIMAL_REGEX, message = "engravingBonus không hợp lệ")
    private String engravingBonus;

    @NotBlank
    @Pattern(regexp = DECIMAL_REGEX, message = "customImageBonusPerImage không hợp lệ")
    private String customImageBonusPerImage;

    @NotNull(message = "thresholds không được để trống")
    @Valid
    private DifficultyThresholdsDto thresholds;
}
