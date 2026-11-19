package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationContextType;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateConversationRequestDto {
    private ConsultationContextType contextType;
    private String contextReference;
    private Integer contextVersion;

    @Size(max = 2000, message = "initialMessage must not exceed 2000 characters")
    private String initialMessage;
}
