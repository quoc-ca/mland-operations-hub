package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Technical / Audit Projection for Reviews (Feature-011).
 * Strictly accessible by ADMIN and MANAGER for technical compliance and audit logs.
 * Customer PII, private body text and direct customer contact data are masked/excluded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewTechnicalProjectionDto {

    private String id;
    private TargetType targetType;
    private String targetReference;
    private Long packageId;
    private Integer rating;
    private ReviewStatus status;
    private Integer imageCount;
    private Integer bodyTextLength;
    private Long version;
    private Boolean hasReply;
    private Instant createdAt;
    private Instant updatedAt;
}
