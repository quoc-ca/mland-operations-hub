package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewAggregateProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.Review;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewPageDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewAggregateDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewPublicReadFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewPublicReadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.FluentQuery;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                reviewRepository, null, replyRepository, null, null, null, null, null, null
        );
        publicReadService = new ReviewPublicReadServiceImpl(
                reviewRepository,
                replyRepository,
                applicationService
        );
    }

    private static ReviewAggregateProjection aggregate(Long count, Double average) {
        return new ReviewAggregateProjection() {
            @Override
            public Long getTotalCount() {
                return count;
            }

            @Override
            public Double getAverageRating() {
                return average;
            }
        };
    }

    private static Review review(String id, Instant createdAt, int rating) {
        return Review.builder().id(id).targetType(TargetType.WORKSHOP_SESSION).targetReference("SESS-100")
                .rating(rating).status(ReviewStatus.PUBLIC).createdAt(createdAt).updatedAt(createdAt).build();
    }

    /** The repository's fluent finder returns these rows whatever the specification, as the real query would for them. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void rowsAre(List<Review> rows) {
        when(reviewRepository.findBy(any(Specification.class), any(Function.class))).thenAnswer(call -> rows);
        when(replyRepository.findByReviewIdIn(any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Public read returns the target's aggregate and its reviews")
    void getPublicReviews_ReturnsAggregateAndReviews() {
        when(reviewRepository.getAggregateByOptionalTarget(TargetType.WORKSHOP_SESSION, "SESS-100", null, ReviewStatus.PUBLIC))
                .thenReturn(aggregate(2L, 4.5));
        when(reviewRepository.count(any(Specification.class))).thenReturn(2L);
        rowsAre(List.of(review("REV-2", Instant.parse("2026-11-06T10:00:00Z"), 5),
                review("REV-1", Instant.parse("2026-11-05T10:00:00Z"), 4)));

        PublicReviewPageDto result = publicReadService.getPublicReviews(
                new PublicReviewQuery(TargetType.WORKSHOP_SESSION, "SESS-100", null, null, null));

        assertThat(result.getAggregate().getCount()).isEqualTo(2L);
        assertThat(result.getAggregate().getAverageRating()).isEqualTo(4.5);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2L);
        assertThat(result.isHasNext()).isFalse();
        assertThat(result.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("No target at all lists every public review, as the contract allows")
    void getPublicReviews_WithoutTarget_ListsAll() {
        when(reviewRepository.getAggregateByOptionalTarget(null, null, null, ReviewStatus.PUBLIC)).thenReturn(aggregate(3L, 4.0));
        when(reviewRepository.count(any(Specification.class))).thenReturn(3L);
        rowsAre(List.of(review("REV-3", Instant.parse("2026-11-06T10:00:00Z"), 5)));

        PublicReviewPageDto result = publicReadService.getPublicReviews(new PublicReviewQuery(null, null, null, null, null));

        assertThat(result.getAggregate().getCount()).isEqualTo(3L);
        assertThat(result.getAggregate().getTargetType()).isNull();
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("A rating filter narrows the list and the total, but not the headline aggregate")
    void getPublicReviews_RatingFilter_DoesNotMoveTheAggregate() {
        when(reviewRepository.getAggregateByOptionalTarget(null, null, null, ReviewStatus.PUBLIC)).thenReturn(aggregate(10L, 4.2));
        when(reviewRepository.count(any(Specification.class))).thenReturn(4L); // four five-star reviews
        rowsAre(List.of(review("REV-9", Instant.parse("2026-11-06T10:00:00Z"), 5)));

        PublicReviewPageDto result = publicReadService.getPublicReviews(new PublicReviewQuery(null, null, 5, null, null));

        assertThat(result.getAggregate().getCount()).isEqualTo(10L);
        assertThat(result.getAggregate().getAverageRating()).isEqualTo(4.2);
        assertThat(result.getTotalElements()).isEqualTo(4L);
    }

    @Test
    @DisplayName("A full page hands back a cursor that resumes after its last review")
    void getPublicReviews_FullPage_ReturnsNextCursor() {
        when(reviewRepository.getAggregateByOptionalTarget(null, null, null, ReviewStatus.PUBLIC)).thenReturn(aggregate(3L, 4.0));
        when(reviewRepository.count(any(Specification.class))).thenReturn(3L);
        // limit 2 asks the repository for 3 rows; getting 3 means there is a next page.
        rowsAre(List.of(review("REV-3", Instant.parse("2026-11-06T10:00:00Z"), 5),
                review("REV-2", Instant.parse("2026-11-05T10:00:00Z"), 4),
                review("REV-1", Instant.parse("2026-11-04T10:00:00Z"), 3)));

        PublicReviewPageDto first = publicReadService.getPublicReviews(new PublicReviewQuery(null, null, null, null, 2));

        assertThat(first.getItems()).hasSize(2);
        assertThat(first.isHasNext()).isTrue();
        assertThat(first.getNextCursor()).isNotBlank();
        assertThat(first.getTotalPages()).isEqualTo(2);

        // The cursor is accepted back and the rest of the list follows.
        rowsAre(List.of(review("REV-1", Instant.parse("2026-11-04T10:00:00Z"), 3)));
        PublicReviewPageDto second = publicReadService.getPublicReviews(
                new PublicReviewQuery(null, null, null, first.getNextCursor(), 2));
        assertThat(second.getItems()).hasSize(1);
        assertThat(second.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("A package filter lists that package's reviews and its own aggregate")
    void getPublicReviews_PackageFilter() {
        when(reviewRepository.getAggregateByOptionalTarget(null, null, 145L, ReviewStatus.PUBLIC)).thenReturn(aggregate(2L, 4.5));
        when(reviewRepository.count(any(Specification.class))).thenReturn(2L);
        rowsAre(List.of(review("REV-2", Instant.parse("2026-11-06T10:00:00Z"), 5),
                review("REV-1", Instant.parse("2026-11-05T10:00:00Z"), 4)));

        PublicReviewPageDto result = publicReadService.getPublicReviews(new PublicReviewQuery(null, null, 145L, null, null, null));

        assertThat(result.getAggregate().getCount()).isEqualTo(2L);
        assertThat(result.getAggregate().getAverageRating()).isEqualTo(4.5);
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("A rating outside 1-5 and a malformed cursor are rejected as invalid input")
    void getPublicReviews_InvalidInput_IsRejected() {
        assertThatThrownBy(() -> publicReadService.getPublicReviews(new PublicReviewQuery(null, null, 6, null, null)))
                .isInstanceOf(ReviewManagementException.class).hasMessageContaining("rating");
        assertThatThrownBy(() -> publicReadService.getPublicReviews(new PublicReviewQuery(null, null, 0, null, null)))
                .isInstanceOf(ReviewManagementException.class);
        assertThatThrownBy(() -> publicReadService.getPublicReviews(new PublicReviewQuery(null, null, null, "not-a-cursor", null)))
                .isInstanceOf(ReviewManagementException.class).hasMessageContaining("cursor");
    }

    @Test
    @DisplayName("Realtime aggregate calculates 0 when no public reviews exist")
    void getRealtimeAggregate_EmptyReturnsZero() {
        when(reviewRepository.getAggregateByOptionalTarget(TargetType.RETAIL_PRODUCT, "PROD-NONE", null, ReviewStatus.PUBLIC))
                .thenReturn(null);

        ReviewAggregateDto agg = publicReadService.getRealtimeAggregate(TargetType.RETAIL_PRODUCT, "PROD-NONE");

        assertThat(agg.getCount()).isEqualTo(0L);
        assertThat(agg.getAverageRating()).isEqualTo(0.0);
    }
}
