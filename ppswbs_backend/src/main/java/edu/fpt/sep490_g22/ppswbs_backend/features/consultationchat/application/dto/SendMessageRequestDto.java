package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageRequestDto {
    @NotBlank(message = "message không được để trống")
    @Size(min = 1, max = 2000, message = "message tối đa 2000 ký tự")
    private String message;
}
