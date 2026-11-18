package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDto {
    private String code;
    private String message;
    private String correlationId;
    private Integer retryAfterSeconds;
}
