package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.OwnerType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.SourceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewDto {
    private String id;
    private SourceType sourceType;
    private String sourceReference;
    private TargetType targetType;
    private String targetReference;
    private OwnerType ownerType;
    private String maskedDisplayName;
    /** The workshop package the reviewed session belongs to (null for a retail review); shown so a review says which package. */
    private Long packageId;
    private String packageName;
    private Integer rating;
    private String bodyText;
    private ReviewStatus status;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private ReplyDto reply;
}

