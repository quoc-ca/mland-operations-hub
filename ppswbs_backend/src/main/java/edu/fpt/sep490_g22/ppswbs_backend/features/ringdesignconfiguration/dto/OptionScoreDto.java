package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.OptionScoreKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public class OptionScoreDto {

    @NotNull(message = "kind không được để trống")
    private OptionScoreKind kind;

    @NotBlank(message = "reference không được để trống")
    @Size(min = 1, max = 128)
    private String reference;

    @NotBlank(message = "score không được để trống")
    @Pattern(regexp = "^(0|[1-9][0-9]{0,5})(\\.[0-9]{1,4})?$", message = "score phải là số thập phân không âm tối đa 4 chữ số thập phân")
    private String score;
}
