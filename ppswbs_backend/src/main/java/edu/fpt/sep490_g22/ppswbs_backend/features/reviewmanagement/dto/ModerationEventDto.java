package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ModerationAction;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationEventDto {
    private String id;
    private String reviewId;
    private Long actorId;
    private String actorRole;
    private ModerationAction action;
    private ReviewStatus previousStatus;
    private ReviewStatus newStatus;
    private String reason;
    private String correlationId;
    private Instant createdAt;
}

