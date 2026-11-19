package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadStateRequestDto {
    @NotBlank(message = "messageReference là bắt buộc")
    private String messageReference;
}
