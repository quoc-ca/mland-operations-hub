package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DesignResponseDto {

    private String designId;

    private String packageReference;

    private Integer designVersion;

    private String status;

    private ValidationResultDto validation;

    private EvaluationResultDto evaluation;

    private Map<String, Object> componentSnapshot;
}

