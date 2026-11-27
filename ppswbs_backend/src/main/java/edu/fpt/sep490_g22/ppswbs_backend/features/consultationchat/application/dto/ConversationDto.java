package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationContextType;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationSenderRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationDto {
    private String conversationReference;
    private ConsultationContextType contextType;
    private ContextOptionDto contextSnapshot;
    private String assignedLocationReference;
    private ConsultationSenderRole claimantRole;
    private Long claimantUserId;
    private ConsultationStatus status;
    private Long aggregateVersion;
    private Integer unreadCount;
    private Instant updatedAt;
    private Instant createdAt;
}
