package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewPageDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewAggregateDto;

public interface ReviewPublicReadFacade {

    /**
     * One page of PUBLIC reviews, newest first. Target, rating and cursor are all optional; the aggregate describes the
     * selected target scope (all public reviews when no target is given), whatever the rating filter.
     */
    PublicReviewPageDto getPublicReviews(PublicReviewQuery query);

    /** Count and average of the PUBLIC reviews of a target; both target parts are optional (null means any). */
    ReviewAggregateDto getRealtimeAggregate(TargetType targetType, String targetReference);
}
