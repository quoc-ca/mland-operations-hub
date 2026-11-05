package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewAggregateDto {
    private TargetType targetType;
    private String targetReference;
    private Long count;

    private Double averageRating;

}

