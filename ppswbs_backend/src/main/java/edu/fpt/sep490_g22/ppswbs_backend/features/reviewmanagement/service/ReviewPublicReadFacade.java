package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewPageDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewAggregateDto;
import org.springframework.data.domain.Pageable;

public interface ReviewPublicReadFacade {

    PublicReviewPageDto getPublicReviews(TargetType targetType, String targetReference, Pageable pageable);

    ReviewAggregateDto getRealtimeAggregate(TargetType targetType, String targetReference);
}

