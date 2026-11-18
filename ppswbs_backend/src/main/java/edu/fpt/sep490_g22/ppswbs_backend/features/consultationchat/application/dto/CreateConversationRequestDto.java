package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationContextType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateConversationRequestDto {
    @NotNull(message = "contextType là bắt buộc")
    private ConsultationContextType contextType;

    @NotNull(message = "contextReference là bắt buộc")
    private String contextReference;

    @NotNull(message = "contextVersion là bắt buộc")
    private Integer contextVersion;

    @Size(max = 2000, message = "initialMessage tối đa 2000 ký tự")
    private String initialMessage;
}
