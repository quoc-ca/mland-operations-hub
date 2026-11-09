package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RingDesignErrorResponseDto {

    private String code;

    private String message;

    private String correlationId;

    private List<String> details;
}

