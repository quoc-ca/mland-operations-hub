package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("label")
    @JsonAlias({"label", "targetName"})
    private String targetName;

    /** Retail only: the order the line belongs to, the ring bought and its size (null for workshops). */
    private String orderId;
    private String variantId;
    private Integer size;

    private Instant completedAt;
    private boolean eligible;
    private boolean alreadyReviewed;
    private String reviewId;
    private String reasonCode;
    /** The Member's own review of this source, when there is one (the "Đã đánh giá" tab). */
    private ReviewDto review;
}

