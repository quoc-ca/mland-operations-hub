package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
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

    /**
     * Photos are stored as data URLs in a MEDIUMTEXT column (16 MB). The client allows 10 MB of files, which is about
     * 13.4 M base64 characters, so this keeps a margin below the column limit and answers 400 instead of a database error.
     */
    static final int MAX_IMAGE_CHARS = 14_000_000;

    static void requireImagesFit(java.util.List<String> images) {
        if (images == null) return;
        long total = 0;
        for (String image : images) total += image == null ? 0 : image.length();
        if (total > MAX_IMAGE_CHARS) {
            throw ReviewManagementException.badRequest("IMAGES_TOO_LARGE",
                    "Tổng dung lượng hình ảnh quá lớn, vui lòng chọn ảnh nhỏ hơn (tối đa 10MB).");
        }
    }

    private final ReviewRepository reviewRepository;
    private final ReviewRevisionRepository revisionRepository;
    private final ReviewReplyRepository replyRepository;
    private final ReviewEligibilityService eligibilityService;
    private final WorkshopAttendanceReviewPort workshopAttendancePort;
    private final RetailFulfilmentReviewPort retailFulfilmentPort;
    private final ReviewMaskingService maskingService;
    private final ReviewIdempotencyService idempotencyService;
    private final AccountFacade accountFacade;

    private static final java.util.regex.Pattern SQL_INJECTION_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|EXEC|EXECUTE|UNION|DECLARE|CAST)\\b[\\s\\S]*?\\b(FROM|INTO|SET|TABLE|DATABASE|WHERE|ALL|JOIN|ON)\\b)|(--|;|/\\*|\\*/|@@|char\\s*\\(|nchar\\s*\\()",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private void validateSafeContent(String text) {
        if (text != null && SQL_INJECTION_PATTERN.matcher(text).find()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID",
                    "Nội dung đánh giá chứa cú pháp hoặc ký tự không hợp lệ (nguy cơ SQL Injection).");
        }
    }

    private static boolean wantsAnonymous(CreateReviewRequest request) {
        return Boolean.TRUE.equals(request.getAnonymous());
    }

    @Transactional
    public ReviewDto createReview(CreateReviewRequest request, Long authenticatedMemberId, String idempotencyKey) {
        if (request == null) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID",
                    "Dữ liệu tạo đánh giá không được để trống.");
        }
        validateSafeContent(request.getBodyText());

        if (authenticatedMemberId == null) {
            throw ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED",
                    "Yêu cầu đăng nhập tài khoản hội viên để tạo đánh giá.");
        }

        String actorScope = "MEMBER_" + authenticatedMemberId;

        // Idempotency check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String requestHash = idempotencyService.computeHash(request);
            Optional<ReviewDto> existing = idempotencyService.findCompletedResponse(
                    idempotencyKey, actorScope, requestHash, ReviewDto.class);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // Kiểm tra duplicate source
        if (reviewRepository.existsBySourceTypeAndSourceReference(request.getSourceType(),
                request.getSourceReference())) {
            throw ReviewManagementException.conflict("REVIEW_ALREADY_EXISTS",
                    "Trải nghiệm/đơn hàng này đã được gửi đánh giá trước đó.");
        }

        OwnerType ownerType;
        String ownerReference;
        String maskedDisplayName;
        // The package of the attended workshop, kept with the review so it can be shown
        // and filtered by package.
        Long packageId = null;
        String packageName = null;

        if (authenticatedMemberId != null) {
            ownerType = OwnerType.MEMBER;
            ownerReference = String.valueOf(authenticatedMemberId);
            // The reviewer's name on the account, partly masked; or anonymous when they
            // chose so. Never the account number.
            maskedDisplayName = wantsAnonymous(request)
                    ? ReviewMaskingService.ANONYMOUS_DISPLAY_NAME
                    : maskingService.maskMemberDisplayName(authenticatedMemberId,
                            accountFacade == null ? null
                                    : accountFacade.findAccountFullName(authenticatedMemberId).orElse(null));

            // Verify eligibility
            if (request.getSourceType() == SourceType.WORKSHOP_ATTENDANCE) {
                Optional<EligibleSourceItem> itemOpt = workshopAttendancePort.verifySource(
                        request.getSourceReference(), authenticatedMemberId, null, null);
                if (itemOpt.isEmpty()) {
                    throw ReviewManagementException.unprocessable("REVIEW_SOURCE_NOT_ELIGIBLE",
                            "Vé tham gia workshop không hợp lệ hoặc chưa điểm danh.");
                }
                EligibleSourceItem item = itemOpt.get();
                if (request.getTargetType() != TargetType.WORKSHOP_SESSION
                        || !request.getTargetReference().equals(item.getTargetReference())) {
                    throw ReviewManagementException.unprocessable("REVIEW_TARGET_MISMATCH",
                            "Target của đánh giá không khớp với buổi workshop đã tham dự.");
                }
                packageId = item.getPackageId();
                packageName = item.getTargetName();
            } else if (request.getSourceType() == SourceType.RETAIL_ORDER_LINE) {
                Optional<EligibleSourceItem> itemOpt = retailFulfilmentPort.verifySource(
                        request.getSourceReference(), authenticatedMemberId, null, null);
                if (itemOpt.isEmpty()) {
                    throw ReviewManagementException.unprocessable("REVIEW_SOURCE_NOT_ELIGIBLE",
                            "Mục đơn hàng bán lẻ không hợp lệ hoặc chưa được hoàn tất nhận hàng.");
                }
                EligibleSourceItem item = itemOpt.get();
                if (request.getTargetType() != TargetType.RETAIL_PRODUCT
                        || !request.getTargetReference().equals(item.getTargetReference())) {
                    throw ReviewManagementException.unprocessable("REVIEW_TARGET_MISMATCH",
                            "Target của đánh giá không khớp với sản phẩm bán lẻ trong đơn hàng.");
                }
            } else {
                throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID",
                        "Loại nguồn đánh giá không được hỗ trợ.");
            }
        } else {
            throw ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED",
                    "Yêu cầu đăng nhập tài khoản hội viên để tạo đánh giá.");
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
                .packageId(packageId)
                .packageName(packageName)
                .rating(request.getRating())
                .bodyText(request.getBodyText() != null ? request.getBodyText().trim() : null)
                .status(ReviewStatus.PUBLIC)
                // No version here: the id is assigned by us, so a non-null @Version would make
                // Spring Data call merge()
                // on a row that does not exist yet and fail with an optimistic-lock error.
                // Hibernate sets version 0.
                .createdAt(now)
                .updatedAt(now)
                .build();
        requireImagesFit(request.getImageUrls());
        review.setImageList(request.getImageUrls());

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

        if (idempotencyService != null) {
            idempotencyService.recordPending(idempotencyKey, "CREATE_REVIEW", actorScope,
                    idempotencyService.computeHash(request));
        }

        try {
            reviewRepository.save(review);
            revisionRepository.save(revision);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate review detected for source {}:{}", request.getSourceType(),
                    request.getSourceReference());
            throw ReviewManagementException.conflict("REVIEW_ALREADY_EXISTS", "Trải nghiệm này đã được gửi đánh giá.");
        }

        ReviewDto responseDto = mapToReviewDto(review, null, true, true);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.recordSuccess(idempotencyKey, actorScope, responseDto);
        }

        return responseDto;
    }

    @Transactional
    public ReviewDto editReview(String reviewId, EditReviewRequest request, Long authenticatedMemberId,
            String guestHandle, Long expectedVersion, String idempotencyKey) {
        if (reviewId == null || reviewId.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "ID đánh giá không hợp lệ.");
        }
        if (request == null) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID",
                    "Dữ liệu chỉnh sửa không được để trống.");
        }
        validateSafeContent(request.getBodyText());

        Review review = reviewRepository.findById(reviewId.trim())
                .orElseThrow(() -> ReviewManagementException.notFound("REVIEW_NOT_FOUND",
                        "Không tìm thấy đánh giá với ID: " + reviewId));

        if (review.getStatus() == ReviewStatus.REMOVED) {
            throw ReviewManagementException.conflict("REVIEW_STATE_CONFLICT",
                    "Đánh giá đã bị gỡ bỏ, không thể chỉnh sửa.");
        }

        // Ownership authorization
        if (authenticatedMemberId == null) {
            throw ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED",
                    "Yêu cầu đăng nhập tài khoản hội viên để chỉnh sửa đánh giá.");
        }
        if (review.getOwnerType() != OwnerType.MEMBER
                || !String.valueOf(authenticatedMemberId).equals(review.getOwnerReference())) {
            throw ReviewManagementException.forbidden("REVIEW_NOT_AUTHORIZED",
                    "Bạn không có quyền chỉnh sửa đánh giá này.");
        }

        // Optimistic locking
        if (expectedVersion == null || !review.getVersion().equals(expectedVersion)) {
            throw ReviewManagementException.conflict("REVIEW_STATE_CONFLICT",
                    "Phiên bản dữ liệu không khớp (Optimistic Lock Conflict). Vui lòng tải lại trang.");
        }

        String actorScope = authenticatedMemberId != null
                ? "MEMBER_" + authenticatedMemberId
                : "GUEST_" + (guestHandle != null ? eligibilityService.hashHandle(guestHandle) : "ANON");
        String requestHash = idempotencyService != null ? idempotencyService.computeHash(request) : null;
        Optional<ReviewDto> cached = idempotencyService != null ? idempotencyService.findCompletedResponse(
                idempotencyKey, actorScope, requestHash, ReviewDto.class) : Optional.empty();
        if (cached.isPresent())
            return cached.get();
        if (idempotencyService != null)
            idempotencyService.recordPending(idempotencyKey, "EDIT_REVIEW", actorScope, requestHash);

        Instant now = Instant.now();
        review.setRating(request.getRating());
        review.setBodyText(request.getBodyText() != null ? request.getBodyText().trim() : null);
        if (request.getImageUrls() != null) {
            requireImagesFit(request.getImageUrls());
            review.setImageList(request.getImageUrls());
        }
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
        ReviewDto result = mapToReviewDto(review, replyDto, true, true);
        if (idempotencyService != null)
            idempotencyService.recordSuccess(idempotencyKey, actorScope, result);
        return result;
    }

    @Transactional(readOnly = true)
    public ReviewDto getReviewDetail(String reviewId, CurrentAccountActor actor) {
        if (reviewId == null || reviewId.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "ID đánh giá không hợp lệ.");
        }
        Review review = reviewRepository.findById(reviewId.trim())
                .orElseThrow(() -> ReviewManagementException.notFound("REVIEW_NOT_FOUND",
                        "Không tìm thấy đánh giá với ID: " + reviewId));

        boolean owner = actor != null && actor.role() == ActorRole.MEMBER
                && review.getOwnerType() == OwnerType.MEMBER
                && String.valueOf(actor.accountId()).equals(review.getOwnerReference());
        boolean isInternalModerator = actor != null
                && (actor.role() == ActorRole.STAFF || actor.role() == ActorRole.MANAGER);
        if (review.getStatus() != ReviewStatus.PUBLIC && !owner && !isInternalModerator) {
            throw ReviewManagementException.notFound("REVIEW_NOT_FOUND", "Không tìm thấy review.");
        }
        ReplyDto replyDto = replyRepository.findByReviewId(reviewId)
                .map(reply -> mapToReplyDto(reply, owner || isInternalModerator)).orElse(null);
        boolean canEdit = owner && review.getStatus() != ReviewStatus.REMOVED;
        return mapToReviewDto(review, replyDto, owner || isInternalModerator, canEdit);
    }

    @Transactional(readOnly = true)
    public edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewTechnicalProjectionDto getTechnicalProjection(String reviewId) {
        if (reviewId == null || reviewId.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "ID đánh giá không hợp lệ.");
        }
        Review review = reviewRepository.findById(reviewId.trim())
                .orElseThrow(() -> ReviewManagementException.notFound("REVIEW_NOT_FOUND",
                        "Không tìm thấy đánh giá với ID: " + reviewId));
        boolean hasReply = replyRepository.findByReviewId(reviewId.trim()).isPresent();
        return edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewTechnicalProjectionDto.builder()
                .id(review.getId())
                .targetType(review.getTargetType())
                .targetReference(review.getTargetReference())
                .packageId(review.getPackageId())
                .rating(review.getRating())
                .status(review.getStatus())
                .imageCount(review.getImageList() != null ? review.getImageList().size() : 0)
                .bodyTextLength(review.getBodyText() != null ? review.getBodyText().length() : 0)
                .version(review.getVersion())
                .hasReply(hasReply)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    @Deprecated
    public ReviewDto getReviewDetail(String reviewId) {
        return getReviewDetail(reviewId, null);
    }

    public ReviewDto mapToReviewDto(Review review, ReplyDto reply) {
        return mapToReviewDto(review, reply, false, false);
    }

    public ReviewDto mapToReviewDto(Review review, ReplyDto reply, boolean includeOwnerData) {
        return mapToReviewDto(review, reply, includeOwnerData, false);
    }

    public ReviewDto mapToReviewDto(Review review, ReplyDto reply, boolean includeOwnerData, boolean canEdit) {
        return ReviewDto.builder()
                .id(review.getId())
                .sourceType(includeOwnerData ? review.getSourceType() : null)
                .sourceReference(includeOwnerData ? review.getSourceReference() : null)
                .targetType(review.getTargetType())
                .targetReference(review.getTargetReference())
                .ownerType(includeOwnerData ? review.getOwnerType() : null)
                .maskedDisplayName(review.getMaskedDisplayName())
                .packageId(review.getPackageId())
                .packageName(review.getPackageName())
                .rating(review.getRating())
                .bodyText(review.getBodyText())
                .status(review.getStatus())
                .imageUrls(review.getImageList())
                .version(review.getVersion())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .reply(reply)
                .canEdit(canEdit)
                .build();
    }

    public ReplyDto mapToReplyDto(ReviewReply reply) {
        return mapToReplyDto(reply, true);
    }

    public ReplyDto mapToReplyDto(ReviewReply reply, boolean includeTechnicalOwner) {
        return ReplyDto.builder()
                .id(reply.getId())
                .reviewId(reply.getReviewId())
                .bodyText(reply.getBodyText())
                .createdByManagerId(includeTechnicalOwner ? reply.getCreatedByManagerId() : null)
                .version(reply.getVersion())
                .createdAt(reply.getCreatedAt())
                .updatedAt(reply.getUpdatedAt())
                .build();
    }
}
