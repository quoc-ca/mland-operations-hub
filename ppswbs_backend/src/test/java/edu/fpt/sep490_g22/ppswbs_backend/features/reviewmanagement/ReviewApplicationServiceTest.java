package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.CreateReviewRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.EditReviewRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.EligibleSourceItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.RetailFulfilmentReviewPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.WorkshopAttendanceReviewPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewApplicationServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewRevisionRepository revisionRepository;

    @Mock
    private ReviewReplyRepository replyRepository;

    @Mock
    private ReviewEligibilityService eligibilityService;

    @Mock
    private WorkshopAttendanceReviewPort workshopAttendancePort;

    @Mock
    private RetailFulfilmentReviewPort retailFulfilmentPort;

    @Mock
    private ReviewIdempotencyService idempotencyService;

    private ReviewMaskingService maskingService;
    private ReviewApplicationService applicationService;

    @BeforeEach
    void setUp() {
        maskingService = new ReviewMaskingService();
        applicationService = new ReviewApplicationService(
                reviewRepository,
                revisionRepository,
                replyRepository,
                eligibilityService,
                workshopAttendancePort,
                retailFulfilmentPort,
                maskingService,
                idempotencyService
        );
    }

    @Test
    @DisplayName("Member creates workshop review successfully with verified attendance")
    void createReview_MemberWorkshop_Success() {
        CreateReviewRequest req = CreateReviewRequest.builder()
                .sourceType(SourceType.WORKSHOP_ATTENDANCE)
                .sourceReference("TICK-100")
                .targetType(TargetType.WORKSHOP_SESSION)
                .targetReference("SESS-100")
                .rating(5)
                .bodyText("Buổi học làm gốm rất thú vị!")
                .build();

        Long memberId = 12345L;

        EligibleSourceItem item = EligibleSourceItem.builder()
                .sourceType(SourceType.WORKSHOP_ATTENDANCE)
                .sourceReference("TICK-100")
                .targetType(TargetType.WORKSHOP_SESSION)
                .targetReference("SESS-100")
                .memberAccountId(memberId)
                .build();

        when(reviewRepository.existsBySourceTypeAndSourceReference(SourceType.WORKSHOP_ATTENDANCE, "TICK-100"))
                .thenReturn(false);
        when(workshopAttendancePort.verifySource("TICK-100", memberId, null, null))
                .thenReturn(Optional.of(item));

        ReviewDto result = applicationService.createReview(req, memberId, "create-member-000001");

        assertThat(result.getId()).isNotBlank();
        assertThat(result.getRating()).isEqualTo(5);
        assertThat(result.getBodyText()).isEqualTo("Buổi học làm gốm rất thú vị!");
        assertThat(result.getOwnerType()).isEqualTo(OwnerType.MEMBER);
        assertThat(result.getStatus()).isEqualTo(ReviewStatus.PUBLIC);
        assertThat(result.getMaskedDisplayName()).startsWith("Hội viên #1***45");

        verify(reviewRepository).save(any(Review.class));
        verify(revisionRepository).save(any(ReviewRevision.class));
    }

    @Test
    @DisplayName("Create review throws 409 conflict when source was already reviewed")
    void createReview_DuplicateSource_ThrowsConflict() {
        CreateReviewRequest req = CreateReviewRequest.builder()
                .sourceType(SourceType.WORKSHOP_ATTENDANCE)
                .sourceReference("TICK-100")
                .targetType(TargetType.WORKSHOP_SESSION)
                .targetReference("SESS-100")
                .rating(4)
                .build();

        when(reviewRepository.existsBySourceTypeAndSourceReference(SourceType.WORKSHOP_ATTENDANCE, "TICK-100"))
                .thenReturn(true);

        assertThatThrownBy(() -> applicationService.createReview(req, 12345L, "create-duplicate-0001"))
                .isInstanceOf(ReviewManagementException.class)
                .hasMessageContaining("đã được gửi đánh giá trước đó");
    }

    @Test
    @DisplayName("Create review throws 403 when source attendance is not verified")
    void createReview_NotEligible_ThrowsForbidden() {
        CreateReviewRequest req = CreateReviewRequest.builder()
                .sourceType(SourceType.WORKSHOP_ATTENDANCE)
                .sourceReference("TICK-UNATTENDED")
                .targetType(TargetType.WORKSHOP_SESSION)
                .targetReference("SESS-100")
                .rating(4)
                .build();

        when(reviewRepository.existsBySourceTypeAndSourceReference(SourceType.WORKSHOP_ATTENDANCE, "TICK-UNATTENDED"))
                .thenReturn(false);
        when(workshopAttendancePort.verifySource("TICK-UNATTENDED", 12345L, null, null))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.createReview(req, 12345L, "create-invalid-0001"))
                .isInstanceOf(ReviewManagementException.class)
                .hasMessageContaining("Vé tham gia workshop không hợp lệ hoặc chưa điểm danh");
    }

    @Test
    @DisplayName("Owner edits review successfully and appends new revision with optimistic locking check")
    void editReview_Success() {
        String reviewId = "REV-UUID-01";
        Review review = Review.builder()
                .id(reviewId)
                .sourceType(SourceType.WORKSHOP_ATTENDANCE)
                .sourceReference("TICK-100")
                .targetType(TargetType.WORKSHOP_SESSION)
                .targetReference("SESS-100")
                .ownerType(OwnerType.MEMBER)
                .ownerReference("12345")
                .maskedDisplayName("Hội viên #1***45")
                .rating(4)
                .bodyText("Ban đầu đánh giá 4 sao")
                .status(ReviewStatus.PUBLIC)
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        EditReviewRequest editReq = EditReviewRequest.builder()
                .rating(5)
                .bodyText("Cập nhật lại thành 5 sao tuyệt vời!")
                .reason("Cập nhật cảm nhận")
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(revisionRepository.findTopByReviewIdOrderByRevisionNumberDesc(reviewId))
                .thenReturn(Optional.of(ReviewRevision.builder().revisionNumber(1).build()));

        ReviewDto updated = applicationService.editReview(reviewId, editReq, 12345L, null, 0L, "edit-review-000001");

        assertThat(updated.getRating()).isEqualTo(5);
        assertThat(updated.getBodyText()).isEqualTo("Cập nhật lại thành 5 sao tuyệt vời!");

        ArgumentCaptor<ReviewRevision> captor = ArgumentCaptor.forClass(ReviewRevision.class);
        verify(revisionRepository).save(captor.capture());
        assertThat(captor.getValue().getRevisionNumber()).isEqualTo(2);
        assertThat(captor.getValue().getReason()).isEqualTo("Cập nhật cảm nhận");
    }

    @Test
    @DisplayName("Edit review throws 409 when expectedVersion does not match current version")
    void editReview_OptimisticLockConflict_ThrowsConflict() {
        String reviewId = "REV-UUID-01";
        Review review = Review.builder()
                .id(reviewId)
                .ownerType(OwnerType.MEMBER)
                .ownerReference("12345")
                .status(ReviewStatus.PUBLIC)
                .version(2L)
                .build();

        EditReviewRequest editReq = EditReviewRequest.builder()
                .rating(5)
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> applicationService.editReview(reviewId, editReq, 12345L, null, 1L, "edit-stale-000001"))
                .isInstanceOf(ReviewManagementException.class)
                .hasMessageContaining("Optimistic Lock Conflict");
    }

    @Test
    @DisplayName("Edit review throws 403 when non-owner attempts to edit")
    void editReview_UnauthorizedActor_ThrowsForbidden() {
        String reviewId = "REV-UUID-01";
        Review review = Review.builder()
                .id(reviewId)
                .ownerType(OwnerType.MEMBER)
                .ownerReference("12345")
                .status(ReviewStatus.PUBLIC)
                .version(0L)
                .build();

        EditReviewRequest editReq = EditReviewRequest.builder()
                .rating(5)
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> applicationService.editReview(reviewId, editReq, 99999L, null, 0L, "edit-owner-000001")) // Wrong member
                .isInstanceOf(ReviewManagementException.class)
                .hasMessageContaining("không có quyền chỉnh sửa đánh giá này");
    }
}

