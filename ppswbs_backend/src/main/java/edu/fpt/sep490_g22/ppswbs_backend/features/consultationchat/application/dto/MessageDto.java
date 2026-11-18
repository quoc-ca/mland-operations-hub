package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationSenderRole;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDto {
    private String messageReference;
    private ConsultationSenderRole senderRole;
    private String text;
    private Instant createdAt;
}
