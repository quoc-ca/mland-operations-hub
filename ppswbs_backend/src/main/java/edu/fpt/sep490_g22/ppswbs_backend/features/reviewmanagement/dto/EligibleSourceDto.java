package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.SourceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibleSourceDto {
    private SourceType sourceType;
    private String sourceReference;
    private TargetType targetType;
    private String targetReference;
    private String targetName;
    private Instant completedAt;
    private boolean eligible;
    private boolean alreadyReviewed;
    private String reasonCode;
}

