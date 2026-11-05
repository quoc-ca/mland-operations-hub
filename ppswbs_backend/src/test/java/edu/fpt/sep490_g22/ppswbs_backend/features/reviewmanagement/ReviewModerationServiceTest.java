package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewModerationEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ModerationItemDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.VisibilityCommandRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewModerationServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewModerationEventRepository moderationEventRepository;

    @Mock
    private ReviewReplyRepository replyRepository;

    @Mock
    private ReviewApplicationService applicationService;

    private ReviewModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ReviewModerationService(
                reviewRepository,
                moderationEventRepository,
                replyRepository,
                applicationService
        );
    }

    @Test
    @DisplayName("Staff can globally hide a review with audit trail")
    void changeVisibility_StaffHide_Success() {
        String reviewId = "REV-01";
        Review review = Review.builder()
                .id(reviewId)
                .status(ReviewStatus.PUBLIC)
                .version(0L)
                .build();

        VisibilityCommandRequest req = VisibilityCommandRequest.builder()
                .action(ModerationAction.HIDE)
                .reason("Nội dung chứa từ ngữ nhạy cảm cần ẩn tạm thời")
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(replyRepository.findByReviewId(reviewId)).thenReturn(Optional.empty());
        when(moderationEventRepository.findByReviewIdOrderByCreatedAtDesc(eq(reviewId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        ModerationItemDto result = moderationService.changeVisibility(reviewId, req, 777L, "STAFF");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.HIDDEN);

        ArgumentCaptor<ReviewModerationEvent> captor = ArgumentCaptor.forClass(ReviewModerationEvent.class);
        verify(moderationEventRepository).save(captor.capture());
        ReviewModerationEvent event = captor.getValue();
        assertThat(event.getReviewId()).isEqualTo(reviewId);
        assertThat(event.getActorId()).isEqualTo(777L);
        assertThat(event.getActorRole()).isEqualTo("STAFF");
        assertThat(event.getAction()).isEqualTo(ModerationAction.HIDE);
        assertThat(event.getPreviousStatus()).isEqualTo(ReviewStatus.PUBLIC);
        assertThat(event.getNewStatus()).isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    @DisplayName("Staff is forbidden from restoring or removing reviews")
    void changeVisibility_StaffRestoreOrRemove_Forbidden() {
        VisibilityCommandRequest reqRestore = VisibilityCommandRequest.builder()
                .action(ModerationAction.RESTORE)
                .reason("Muốn phục hồi lại")
                .build();

        assertThatThrownBy(() -> moderationService.changeVisibility("REV-01", reqRestore, 777L, "STAFF"))
                .isInstanceOf(ReviewManagementException.class)
                .hasMessageContaining("Nhân viên Staff chỉ có quyền ẩn đánh giá (HIDE)");

        VisibilityCommandRequest reqRemove = VisibilityCommandRequest.builder()
                .action(ModerationAction.REMOVE)
                .reason("Muốn xóa bỏ")
                .build();

        assertThatThrownBy(() -> moderationService.changeVisibility("REV-01", reqRemove, 777L, "STAFF"))
                .isInstanceOf(ReviewManagementException.class)
                .hasMessageContaining("Nhân viên Staff chỉ có quyền ẩn đánh giá (HIDE)");
    }

    @Test
    @DisplayName("Manager can remove a review with audit trail")
    void changeVisibility_ManagerRemove_Success() {
        String reviewId = "REV-02";
        Review review = Review.builder()
                .id(reviewId)
                .status(ReviewStatus.PUBLIC)
                .version(0L)
                .build();

        VisibilityCommandRequest req = VisibilityCommandRequest.builder()
                .action(ModerationAction.REMOVE)
                .reason("Đánh giá spam và bôi nhọ vô căn cứ")
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(replyRepository.findByReviewId(reviewId)).thenReturn(Optional.empty());
        when(moderationEventRepository.findByReviewIdOrderByCreatedAtDesc(eq(reviewId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        ModerationItemDto result = moderationService.changeVisibility(reviewId, req, 888L, "MANAGER");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.REMOVED);

        ArgumentCaptor<ReviewModerationEvent> captor = ArgumentCaptor.forClass(ReviewModerationEvent.class);
        verify(moderationEventRepository).save(captor.capture());
        ReviewModerationEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo(ModerationAction.REMOVE);
        assertThat(event.getNewStatus()).isEqualTo(ReviewStatus.REMOVED);
    }
}

