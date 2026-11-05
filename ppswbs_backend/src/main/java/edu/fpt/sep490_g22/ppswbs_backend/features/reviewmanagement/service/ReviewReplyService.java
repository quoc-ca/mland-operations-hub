package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewModerationEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ModerationAction;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.Review;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewModerationEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewReply;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReplyCommandRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReplyDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewReplyService {

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository replyRepository;
    private final ReviewModerationEventRepository moderationEventRepository;
    private final ReviewApplicationService applicationService;

    @Transactional
    public ReplyDto createReply(String reviewId, ReplyCommandRequest request, Long managerId) {
        if (reviewId == null || reviewId.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "ID đánh giá không hợp lệ.");
        }
        if (request == null || request.getBodyText() == null || request.getBodyText().isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "Nội dung phản hồi không được để trống.");
        }
        if (managerId == null) {
            throw ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED", "Yêu cầu đăng nhập tài khoản Quản lý (Manager).");
        }

        Review review = reviewRepository.findById(reviewId.trim())
                .orElseThrow(() -> ReviewManagementException.notFound("REVIEW_NOT_FOUND", "Không tìm thấy đánh giá với ID: " + reviewId));

        if (replyRepository.findByReviewId(reviewId.trim()).isPresent()) {
            throw ReviewManagementException.conflict("REVIEW_REPLY_ALREADY_EXISTS", "Đánh giá này đã có phản hồi chính thức từ quản lý.");
        }

        Instant now = Instant.now();
        ReviewReply reply = ReviewReply.builder()
                .id(UUID.randomUUID().toString())
                .reviewId(reviewId.trim())
                .bodyText(request.getBodyText().trim())
                .createdByManagerId(managerId)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ReviewModerationEvent event = ReviewModerationEvent.builder()
                .id(UUID.randomUUID().toString())
                .reviewId(reviewId.trim())
                .actorId(managerId)
                .actorRole("MANAGER")
                .action(ModerationAction.CREATE_REPLY)
                .previousStatus(review.getStatus())
                .newStatus(review.getStatus())
                .reason("Quản lý tạo phản hồi chính thức")
                .idempotencyKey(request.getIdempotencyKey())
                .createdAt(now)
                .build();

        replyRepository.save(reply);
        moderationEventRepository.save(event);

        return applicationService.mapToReplyDto(reply);
    }

    @Transactional
    public ReplyDto editReply(String reviewId, ReplyCommandRequest request, Long managerId) {
        if (reviewId == null || reviewId.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "ID đánh giá không hợp lệ.");
        }
        if (request == null || request.getBodyText() == null || request.getBodyText().isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "Nội dung phản hồi không được để trống.");
        }
        if (managerId == null) {
            throw ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED", "Yêu cầu đăng nhập tài khoản Quản lý (Manager).");
        }

        ReviewReply reply = replyRepository.findByReviewId(reviewId.trim())
                .orElseThrow(() -> ReviewManagementException.notFound("REVIEW_REPLY_NOT_FOUND", "Không tìm thấy phản hồi của đánh giá này."));

        if (request.getExpectedVersion() != null && !reply.getVersion().equals(request.getExpectedVersion())) {
            throw ReviewManagementException.conflict("REVIEW_STATE_CONFLICT", "Phiên bản phản hồi không khớp, dữ liệu đã thay đổi.");
        }

        Instant now = Instant.now();
        reply.setBodyText(request.getBodyText().trim());
        reply.setUpdatedAt(now);

        ReviewModerationEvent event = ReviewModerationEvent.builder()
                .id(UUID.randomUUID().toString())
                .reviewId(reviewId.trim())
                .actorId(managerId)
                .actorRole("MANAGER")
                .action(ModerationAction.EDIT_REPLY)
                .reason("Quản lý chỉnh sửa phản hồi chính thức")
                .idempotencyKey(request.getIdempotencyKey())
                .createdAt(now)
                .build();

        replyRepository.save(reply);
        moderationEventRepository.save(event);

        return applicationService.mapToReplyDto(reply);
    }
}

