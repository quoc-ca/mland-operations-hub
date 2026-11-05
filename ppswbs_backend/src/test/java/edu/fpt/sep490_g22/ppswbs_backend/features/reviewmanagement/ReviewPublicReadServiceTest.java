package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewAggregateProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.Review;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewPageDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewAggregateDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewPublicReadFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewPublicReadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewPublicReadServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewReplyRepository replyRepository;

    private ReviewApplicationService applicationService;
    private ReviewPublicReadFacade publicReadService;

    @BeforeEach
    void setUp() {
        applicationService = new ReviewApplicationService(
                reviewRepository, null, replyRepository, null, null, null, null, null
        );
        publicReadService = new ReviewPublicReadServiceImpl(
                reviewRepository,
                replyRepository,
                applicationService
        );
    }

    @Test
    @DisplayName("Public read returns realtime aggregate and public reviews page")
    void getPublicReviews_ReturnsRealtimeAggregateAndReviews() {
        TargetType targetType = TargetType.WORKSHOP_SESSION;
        String targetRef = "SESS-100";
        Pageable pageable = PageRequest.of(0, 10);

        ReviewAggregateProjection proj = new ReviewAggregateProjection() {
            @Override
            public Long getTotalCount() {
                return 2L;
            }

            @Override
            public Double getAverageRating() {
                return 4.5;
            }
        };

        when(reviewRepository.getAggregateByTargetAndStatus(targetType, targetRef, ReviewStatus.PUBLIC))
                .thenReturn(proj);

        Review review1 = Review.builder()
                .id("REV-1")
                .targetType(targetType)
                .targetReference(targetRef)
                .rating(5)
                .status(ReviewStatus.PUBLIC)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Review review2 = Review.builder()
                .id("REV-2")
                .targetType(targetType)
                .targetReference(targetRef)
                .rating(4)
                .status(ReviewStatus.PUBLIC)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(reviewRepository.findByTargetTypeAndTargetReferenceAndStatus(targetType, targetRef, ReviewStatus.PUBLIC, pageable))
                .thenReturn(new PageImpl<>(List.of(review1, review2), pageable, 2));
        when(replyRepository.findByReviewIdIn(any())).thenReturn(Collections.emptyList());

        PublicReviewPageDto result = publicReadService.getPublicReviews(targetType, targetRef, pageable);

        assertThat(result.getAggregate().getTotalCount()).isEqualTo(2L);
        assertThat(result.getAggregate().getAverageRating()).isEqualTo(4.5);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Realtime aggregate calculates 0 when no public reviews exist")
    void getRealtimeAggregate_EmptyReturnsZero() {
        TargetType targetType = TargetType.RETAIL_PRODUCT;
        String targetRef = "PROD-NONE";

        when(reviewRepository.getAggregateByTargetAndStatus(targetType, targetRef, ReviewStatus.PUBLIC))
                .thenReturn(null);

        ReviewAggregateDto agg = publicReadService.getRealtimeAggregate(targetType, targetRef);

        assertThat(agg.getTotalCount()).isEqualTo(0L);
        assertThat(agg.getAverageRating()).isEqualTo(0.0);
    }
}

