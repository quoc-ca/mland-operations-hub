package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewModerationEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ModerationAction;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.Review;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewModerationEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewReply;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReplyCommandRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReplyDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewReplyService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewReplyServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewReplyRepository replyRepository;

    @Mock
    private ReviewModerationEventRepository moderationEventRepository;

    private ReviewApplicationService applicationService;
    private ReviewReplyService replyService;

    @BeforeEach
    void setUp() {
        applicationService = new ReviewApplicationService(
                reviewRepository, null, replyRepository, null, null, null, null, null, null
        );
        replyService = new ReviewReplyService(
                reviewRepository,
                replyRepository,
                moderationEventRepository,
                applicationService
        );
    }

    @Test
    @DisplayName("Manager creates reply successfully with audit event")
    void createReply_Success() {
        String reviewId = "REV-REPLY-01";
        Review review = Review.builder().id(reviewId).status(ReviewStatus.PUBLIC).build();

        ReplyCommandRequest req = ReplyCommandRequest.builder()
                .bodyText("Cảm ơn bạn đã trải nghiệm workshop tại Mland!")
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(replyRepository.findByReviewId(reviewId)).thenReturn(Optional.empty());

        ReplyDto reply = replyService.createReply(reviewId, req, 999L, "reply-create-000001");

        assertThat(reply.getBodyText()).isEqualTo("Cảm ơn bạn đã trải nghiệm workshop tại Mland!");
        assertThat(reply.getCreatedByManagerId()).isEqualTo(999L);

        // Regression: a new reply has an assigned id, so it must be saved with no version (see createReview).
        ArgumentCaptor<ReviewReply> savedReply = ArgumentCaptor.forClass(ReviewReply.class);
        verify(replyRepository).save(savedReply.capture());
        assertThat(savedReply.getValue().getVersion()).isNull();
        ArgumentCaptor<ReviewModerationEvent> captor = ArgumentCaptor.forClass(ReviewModerationEvent.class);
        verify(moderationEventRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(ModerationAction.CREATE_REPLY);
        assertThat(captor.getValue().getActorRole()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("Manager creating duplicate reply throws 409 conflict")
    void createReply_Duplicate_ThrowsConflict() {
        String reviewId = "REV-REPLY-01";
        Review review = Review.builder().id(reviewId).build();

        ReplyCommandRequest req = ReplyCommandRequest.builder()
                .bodyText("Phản hồi lần thứ hai")
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(replyRepository.findByReviewId(reviewId))
                .thenReturn(Optional.of(ReviewReply.builder().id("REP-01").build()));

        assertThatThrownBy(() -> replyService.createReply(reviewId, req, 999L, "reply-duplicate-0001"))
                .isInstanceOf(ReviewManagementException.class)
                .hasMessageContaining("đã có phản hồi chính thức từ quản lý");
    }

    @Test
    @DisplayName("Manager edits existing reply successfully")
    void editReply_Success() {
        String reviewId = "REV-REPLY-01";
        ReviewReply existing = ReviewReply.builder()
                .id("REP-01")
                .reviewId(reviewId)
                .bodyText("Phản hồi cũ")
                .createdByManagerId(999L)
                .version(1L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ReplyCommandRequest req = ReplyCommandRequest.builder()
                .bodyText("Phản hồi mới đã được hiệu chỉnh")
                .build();

        when(replyRepository.findByReviewId(reviewId)).thenReturn(Optional.of(existing));

        ReplyDto updated = replyService.editReply(reviewId, req, 999L, 1L, "reply-edit-000001");

        assertThat(updated.getBodyText()).isEqualTo("Phản hồi mới đã được hiệu chỉnh");
        verify(replyRepository).save(existing);
        verify(moderationEventRepository).save(any(ReviewModerationEvent.class));
    }
}

