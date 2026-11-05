package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.EligibleSourceItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.RetailFulfilmentReviewPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.WorkshopAttendanceReviewPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewApplicationService {

    private final ReviewRepository reviewRepository;
    private final ReviewRevisionRepository revisionRepository;
    private final ReviewReplyRepository replyRepository;
    private final ReviewEligibilityService eligibilityService;
    private final WorkshopAttendanceReviewPort workshopAttendancePort;
    private final RetailFulfilmentReviewPort retailFulfilmentPort;
    private final ReviewMaskingService maskingService;
    private final ReviewIdempotencyService idempotencyService;

    @Transactional
    public ReviewDto createReview(CreateReviewRequest request, Long authenticatedMemberId) {
        if (request == null) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "Dữ liệu tạo đánh giá không được để trống.");
        }

        String actorScope = authenticatedMemberId != null
                ? "MEMBER_" + authenticatedMemberId
                : "GUEST_" + (request.getGuestHandle() != null ? eligibilityService.hashHandle(request.getGuestHandle()) : "ANON");

        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<ReviewDto> existing = idempotencyService.findCompletedResponse(request.getIdempotencyKey(), actorScope, ReviewDto.class);
            if (existing.isPresent()) {
                return existing.get();
            }
            idempotencyService.recordPending(request.getIdempotencyKey(), "CREATE_REVIEW", actorScope, idempotencyService.computeHash(request));
        }

        // Kiểm tra duplicate source
        if (reviewRepository.existsBySourceTypeAndSourceReference(request.getSourceType(), request.getSourceReference())) {
            throw ReviewManagementException.conflict("REVIEW_ALREADY_EXISTS", "Trải nghiệm/đơn hàng này đã được gửi đánh giá trước đó.");
        }

        OwnerType ownerType;
        String ownerReference;
        String maskedDisplayName;

        if (authenticatedMemberId != null) {
            ownerType = OwnerType.MEMBER;
            ownerReference = String.valueOf(authenticatedMemberId);
            maskedDisplayName = maskingService.maskMemberDisplayName(authenticatedMemberId, null);

            // Verify eligibility
            if (request.getSourceType() == SourceType.WORKSHOP_ATTENDANCE) {
                Optional<EligibleSourceItem> itemOpt = workshopAttendancePort.verifySource(
                        request.getSourceReference(), authenticatedMemberId, null, null);
                if (itemOpt.isEmpty()) {
                    throw ReviewManagementException.forbidden("REVIEW_SOURCE_NOT_ELIGIBLE", "Vé tham gia workshop không hợp lệ hoặc chưa điểm danh.");
                }
                EligibleSourceItem item = itemOpt.get();
                if (request.getTargetType() != TargetType.WORKSHOP_SESSION || !request.getTargetReference().equals(item.getTargetReference())) {
                    throw ReviewManagementException.badRequest("REVIEW_TARGET_MISMATCH", "Target của đánh giá không khớp với buổi workshop đã tham dự.");
                }
            } else if (request.getSourceType() == SourceType.RETAIL_ORDER_LINE) {
                Optional<EligibleSourceItem> itemOpt = retailFulfilmentPort.verifySource(
                        request.getSourceReference(), authenticatedMemberId);
                if (itemOpt.isEmpty()) {
                    throw ReviewManagementException.forbidden("REVIEW_SOURCE_NOT_ELIGIBLE", "Mục đơn hàng bán lẻ không hợp lệ hoặc chưa được hoàn tất nhận hàng.");
                }
                EligibleSourceItem item = itemOpt.get();
                if (request.getTargetType() != TargetType.RETAIL_PRODUCT || !request.getTargetReference().equals(item.getTargetReference())) {
                    throw ReviewManagementException.badRequest("REVIEW_TARGET_MISMATCH", "Target của đánh giá không khớp với sản phẩm bán lẻ trong đơn hàng.");
                }
            } else {
                throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "Loại nguồn đánh giá không được hỗ trợ.");
            }
        } else {
            // Guest submission
            if (request.getGuestHandle() == null || request.getGuestHandle().isBlank()) {
                throw ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED", "Yêu cầu mã guestHandle hợp lệ khi chưa đăng nhập.");
            }
            if (request.getSourceType() != SourceType.WORKSHOP_ATTENDANCE) {
                throw ReviewManagementException.forbidden("REVIEW_SOURCE_NOT_ELIGIBLE", "Khách vãng lai chỉ được đánh giá vé tham gia workshop.");
            }

            GuestEligibilityHandle handle = eligibilityService.validateGuestHandle(request.getGuestHandle(), request.getSourceReference());
            ownerType = OwnerType.GUEST;
            ownerReference = handle.getHandleHash();
            maskedDisplayName = handle.getContactValueMasked();

            // Verify attendance ticket matching contact
            Optional<EligibleSourceItem> itemOpt = workshopAttendancePort.verifySource(
                    request.getSourceReference(), null, handle.getContactType(), null);
            // Nếu adapter yêu cầu contactValue để verify, có thể dùng chính item trong handle
            if (itemOpt.isEmpty()) {
                // Kiểm tra trực tiếp qua verifySource với contactType và null value (adapter hỗ trợ filter bằng reference)
                throw ReviewManagementException.forbidden("REVIEW_SOURCE_NOT_ELIGIBLE", "Vé workshop không hợp lệ hoặc chưa điểm danh.");
            }
            EligibleSourceItem item = itemOpt.get();
            if (request.getTargetType() != TargetType.WORKSHOP_SESSION || !request.getTargetReference().equals(item.getTargetReference())) {
                throw ReviewManagementException.badRequest("REVIEW_TARGET_MISMATCH", "Target của đánh giá không khớp với buổi workshop.");
            }
        }

        String reviewId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Review review = Review.builder()
                .id(reviewId)
                .sourceType(request.getSourceType())
                .sourceReference(request.getSourceReference().trim())
                .targetType(request.getTargetType())
                .targetReference(request.getTargetReference().trim())
                .ownerType(ownerType)
                .ownerReference(ownerReference)
                .maskedDisplayName(maskedDisplayName)
                .rating(request.getRating())
                .bodyText(request.getBodyText() != null ? request.getBodyText().trim() : null)
                .status(ReviewStatus.PUBLIC)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ReviewRevision revision = ReviewRevision.builder()
                .id(UUID.randomUUID().toString())
                .reviewId(reviewId)
                .revisionNumber(1)
                .rating(request.getRating())
                .bodyText(request.getBodyText() != null ? request.getBodyText().trim() : null)
                .changedByType(ownerType)
                .changedByReference(ownerReference)
                .changedAt(now)
                .reason("INITIAL_SUBMISSION")
                .build();

        try {
            reviewRepository.save(review);
            revisionRepository.save(revision);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate review detected for source {}:{}", request.getSourceType(), request.getSourceReference());
            throw ReviewManagementException.conflict("REVIEW_ALREADY_EXISTS", "Trải nghiệm này đã được gửi đánh giá.");
        }

        ReviewDto responseDto = mapToReviewDto(review, null);

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            idempotencyService.recordSuccess(request.getIdempotencyKey(), actorScope, responseDto);
        }

        return responseDto;
    }

    @Transactional
    public ReviewDto editReview(String reviewId, EditReviewRequest request, Long authenticatedMemberId) {
        if (reviewId == null || reviewId.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "ID đánh giá không hợp lệ.");
        }
        if (request == null) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "Dữ liệu chỉnh sửa không được để trống.");
        }

        Review review = reviewRepository.findById(reviewId.trim())
                .orElseThrow(() -> ReviewManagementException.notFound("REVIEW_NOT_FOUND", "Không tìm thấy đánh giá với ID: " + reviewId));

        if (review.getStatus() == ReviewStatus.REMOVED) {
            throw ReviewManagementException.conflict("REVIEW_STATE_CONFLICT", "Đánh giá đã bị gỡ bỏ, không thể chỉnh sửa.");
        }

        // Ownership authorization
        if (review.getOwnerType() == OwnerType.MEMBER) {
            if (authenticatedMemberId == null || !String.valueOf(authenticatedMemberId).equals(review.getOwnerReference())) {
                throw ReviewManagementException.forbidden("REVIEW_NOT_AUTHORIZED", "Bạn không có quyền chỉnh sửa đánh giá này.");
            }
        } else if (review.getOwnerType() == OwnerType.GUEST) {
            if (request.getGuestHandle() == null || request.getGuestHandle().isBlank()) {
                throw ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED", "Yêu cầu mã guestHandle hợp lệ để chỉnh sửa đánh giá khách vãng lai.");
            }
            GuestEligibilityHandle handle = eligibilityService.validateGuestHandle(request.getGuestHandle(), review.getSourceReference());
            if (!handle.getHandleHash().equals(review.getOwnerReference())) {
                throw ReviewManagementException.forbidden("REVIEW_NOT_AUTHORIZED", "Mã guestHandle không khớp với chủ sở hữu đánh giá.");
            }
        }

        // Optimistic locking
        if (request.getExpectedVersion() == null || !review.getVersion().equals(request.getExpectedVersion())) {
            throw ReviewManagementException.conflict("REVIEW_STATE_CONFLICT", "Phiên bản dữ liệu không khớp (Optimistic Lock Conflict). Vui lòng tải lại trang.");
        }

        Instant now = Instant.now();
        review.setRating(request.getRating());
        review.setBodyText(request.getBodyText() != null ? request.getBodyText().trim() : null);
        review.setUpdatedAt(now);

        Integer latestRevNumber = revisionRepository.findTopByReviewIdOrderByRevisionNumberDesc(reviewId)
                .map(ReviewRevision::getRevisionNumber)
                .orElse(1);

        ReviewRevision revision = ReviewRevision.builder()
                .id(UUID.randomUUID().toString())
                .reviewId(reviewId)
                .revisionNumber(latestRevNumber + 1)
                .rating(request.getRating())
                .bodyText(request.getBodyText() != null ? request.getBodyText().trim() : null)
                .changedByType(review.getOwnerType())
                .changedByReference(review.getOwnerReference())
                .changedAt(now)
                .reason(request.getReason() != null ? request.getReason().trim() : "OWNER_EDIT")
                .build();

        reviewRepository.save(review);
        revisionRepository.save(revision);

        ReplyDto replyDto = replyRepository.findByReviewId(reviewId).map(this::mapToReplyDto).orElse(null);
        return mapToReviewDto(review, replyDto);
    }

    @Transactional(readOnly = true)
    public ReviewDto getReviewDetail(String reviewId) {
        if (reviewId == null || reviewId.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "ID đánh giá không hợp lệ.");
        }
        Review review = reviewRepository.findById(reviewId.trim())
                .orElseThrow(() -> ReviewManagementException.notFound("REVIEW_NOT_FOUND", "Không tìm thấy đánh giá với ID: " + reviewId));

        ReplyDto replyDto = replyRepository.findByReviewId(reviewId).map(this::mapToReplyDto).orElse(null);
        return mapToReviewDto(review, replyDto);
    }

    public ReviewDto mapToReviewDto(Review review, ReplyDto reply) {
        return ReviewDto.builder()
                .id(review.getId())
                .sourceType(review.getSourceType())
                .sourceReference(review.getSourceReference())
                .targetType(review.getTargetType())
                .targetReference(review.getTargetReference())
                .ownerType(review.getOwnerType())
                .maskedDisplayName(review.getMaskedDisplayName())
                .rating(review.getRating())
                .bodyText(review.getBodyText())
                .status(review.getStatus())
                .version(review.getVersion())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .reply(reply)
                .build();
    }

    public ReplyDto mapToReplyDto(ReviewReply reply) {
        return ReplyDto.builder()
                .id(reply.getId())
                .reviewId(reply.getReviewId())
                .bodyText(reply.getBodyText())
                .createdByManagerId(reply.getCreatedByManagerId())
                .version(reply.getVersion())
                .createdAt(reply.getCreatedAt())
                .updatedAt(reply.getUpdatedAt())
                .build();
    }
}

