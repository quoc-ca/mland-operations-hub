package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewModerationEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewModerationService {

    private final ReviewRepository reviewRepository;
    private final ReviewModerationEventRepository moderationEventRepository;
    private final ReviewReplyRepository replyRepository;
    private final ReviewApplicationService applicationService;

    @Transactional
    public ModerationItemDto changeVisibility(String reviewId, VisibilityCommandRequest request, Long actorId, String actorRole) {
        if (reviewId == null || reviewId.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "ID đánh giá không hợp lệ.");
        }
        if (request == null || request.getAction() == null || request.getReason() == null || request.getReason().isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "Hành động kiểm duyệt và lý do là bắt buộc.");
        }

        String role = actorRole != null ? actorRole.toUpperCase() : "UNKNOWN";
        ModerationAction action = request.getAction();

        // Kiểm tra quyền hạn kiểm duyệt theo vai trò
        if ("STAFF".equals(role)) {
            if (action != ModerationAction.HIDE) {
                throw ReviewManagementException.forbidden("REVIEW_MODERATION_ACTION_NOT_ALLOWED", "Nhân viên Staff chỉ có quyền ẩn đánh giá (HIDE). Không có quyền phục hồi hoặc gỡ bỏ.");
            }
        } else if (!"MANAGER".equals(role) && !"ADMIN".equals(role)) {
            throw ReviewManagementException.forbidden("REVIEW_NOT_AUTHORIZED", "Chỉ Staff hoặc Manager mới có quyền kiểm duyệt đánh giá.");
        }

        Review review = reviewRepository.findById(reviewId.trim())
                .orElseThrow(() -> ReviewManagementException.notFound("REVIEW_NOT_FOUND", "Không tìm thấy đánh giá với ID: " + reviewId));

        ReviewStatus previousStatus = review.getStatus();
        ReviewStatus targetStatus = switch (action) {
            case HIDE -> ReviewStatus.HIDDEN;
            case RESTORE -> ReviewStatus.PUBLIC;
            case REMOVE -> ReviewStatus.REMOVED;
            default -> throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "Hành động kiểm duyệt không hợp lệ.");
        };

        if (previousStatus == targetStatus) {
            // No-op or already in state
            return mapToModerationItemDto(review);
        }

        review.setStatus(targetStatus);
        review.setUpdatedAt(Instant.now());

        ReviewModerationEvent event = ReviewModerationEvent.builder()
                .id(UUID.randomUUID().toString())
                .reviewId(reviewId)
                .actorId(actorId)
                .actorRole(role)
                .action(action)
                .previousStatus(previousStatus)
                .newStatus(targetStatus)
                .reason(request.getReason().trim())
                .idempotencyKey(request.getIdempotencyKey())
                .createdAt(Instant.now())
                .build();

        reviewRepository.save(review);
        moderationEventRepository.save(event);

        return mapToModerationItemDto(review);
    }

    @Transactional(readOnly = true)
    public ModerationPageDto getModerationQueue(ReviewStatus statusFilter, TargetType targetType, String targetReference, Pageable pageable) {
        Specification<Review> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (targetType != null) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (targetReference != null && !targetReference.isBlank()) {
                predicates.add(cb.equal(root.get("targetReference"), targetReference.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Review> page = reviewRepository.findAll(spec, pageable);
        List<ModerationItemDto> items = page.getContent().stream()
                .map(this::mapToModerationItemDto)
                .collect(Collectors.toList());

        return ModerationPageDto.builder()
                .items(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .build();
    }

    public ModerationItemDto mapToModerationItemDto(Review review) {
        ReplyDto replyDto = replyRepository.findByReviewId(review.getId())
                .map(applicationService::mapToReplyDto)
                .orElse(null);

        Page<ReviewModerationEvent> eventsPage = moderationEventRepository.findByReviewIdOrderByCreatedAtDesc(review.getId(), PageRequest.of(0, 5));
        List<ModerationEventDto> recentEvents = eventsPage.getContent().stream()
                .map(e -> ModerationEventDto.builder()
                        .id(e.getId())
                        .reviewId(e.getReviewId())
                        .actorId(e.getActorId())
                        .actorRole(e.getActorRole())
                        .action(e.getAction())
                        .previousStatus(e.getPreviousStatus())
                        .newStatus(e.getNewStatus())
                        .reason(e.getReason())
                        .correlationId(e.getCorrelationId())
                        .createdAt(e.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ModerationItemDto.builder()
                .id(review.getId())
                .sourceType(review.getSourceType())
                .sourceReference(review.getSourceReference())
                .targetType(review.getTargetType())
                .targetReference(review.getTargetReference())
                .ownerType(review.getOwnerType())
                .ownerReference(review.getOwnerReference())
                .maskedDisplayName(review.getMaskedDisplayName())
                .rating(review.getRating())
                .bodyText(review.getBodyText())
                .status(review.getStatus())
                .version(review.getVersion())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .reply(replyDto)
                .recentEvents(recentEvents)
                .build();
    }
}

