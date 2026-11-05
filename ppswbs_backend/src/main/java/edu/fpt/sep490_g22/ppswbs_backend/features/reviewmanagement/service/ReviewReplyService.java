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
    private final ReviewIdempotencyService idempotencyService;

    @Deprecated
    public ReviewReplyService(ReviewRepository reviewRepository,
                              ReviewReplyRepository replyRepository,
                              ReviewModerationEventRepository moderationEventRepository,
                              ReviewApplicationService applicationService) {
        this(reviewRepository, replyRepository, moderationEventRepository, applicationService, null);
    }


    @Transactional
    public ReplyDto createReply(String reviewId, ReplyCommandRequest request, Long managerId, String idempotencyKey) {
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

        if (review.getOwnerType() == edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.OwnerType.MEMBER
                && String.valueOf(managerId).equals(review.getOwnerReference())) {
            throw ReviewManagementException.forbidden("REVIEW_NOT_AUTHORIZED", "Manager cannot reply to their own review.");
        }

        if (replyRepository.findByReviewId(reviewId.trim()).isPresent()) {
            throw ReviewManagementException.conflict("REVIEW_REPLY_ALREADY_EXISTS", "Đánh giá này đã có phản hồi chính thức từ quản lý.");
        }

        String actorScope = "MANAGER_" + managerId;
        String requestHash = idempotencyService != null ? idempotencyService.computeHash(request) : null;
        java.util.Optional<ReplyDto> cached = idempotencyService != null ? idempotencyService
                .findCompletedResponse(idempotencyKey, actorScope, requestHash, ReplyDto.class) : java.util.Optional.empty();
        if (cached.isPresent()) return cached.get();
        if (idempotencyService != null) idempotencyService.recordPending(idempotencyKey, "REVIEW_REPLY_CREATE", actorScope, requestHash);

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
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .build();

        replyRepository.save(reply);
        moderationEventRepository.save(event);

        ReplyDto result = applicationService.mapToReplyDto(reply);
        if (idempotencyService != null) idempotencyService.recordSuccess(idempotencyKey, actorScope, result);
        return result;
    }

    @Transactional
    public ReplyDto editReply(String reviewId, ReplyCommandRequest request, Long managerId,
                              Long expectedVersion, String idempotencyKey) {
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

        if (expectedVersion == null || !reply.getVersion().equals(expectedVersion)) {
            throw ReviewManagementException.conflict("REVIEW_STATE_CONFLICT", "Phiên bản phản hồi không khớp, dữ liệu đã thay đổi.");
        }

        String actorScope = "MANAGER_" + managerId;
        String requestHash = idempotencyService != null ? idempotencyService.computeHash(request) : null;
        java.util.Optional<ReplyDto> cached = idempotencyService != null ? idempotencyService
                .findCompletedResponse(idempotencyKey, actorScope, requestHash, ReplyDto.class) : java.util.Optional.empty();
        if (cached.isPresent()) return cached.get();
        if (idempotencyService != null) idempotencyService.recordPending(idempotencyKey, "REVIEW_REPLY_EDIT", actorScope, requestHash);

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
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .build();

        replyRepository.save(reply);
        moderationEventRepository.save(event);

        ReplyDto result = applicationService.mapToReplyDto(reply);
        if (idempotencyService != null) idempotencyService.recordSuccess(idempotencyKey, actorScope, result);
        return result;
    }
}

