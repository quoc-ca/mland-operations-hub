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

    private final ReviewRepository reviewRepository;
    private final ReviewRevisionRepository revisionRepository;
    private final ReviewReplyRepository replyRepository;
    private final ReviewEligibilityService eligibilityService;
    private final WorkshopAttendanceReviewPort workshopAttendancePort;
    private final RetailFulfilmentReviewPort retailFulfilmentPort;
    private final ReviewMaskingService maskingService;
    private final ReviewIdempotencyService idempotencyService;
    private final AccountFacade accountFacade;

    private static boolean wantsAnonymous(CreateReviewRequest request) {
        return Boolean.TRUE.equals(request.getAnonymous());
    }

    @Transactional
    public ReviewDto createReview(CreateReviewRequest request, Long authenticatedMemberId, String idempotencyKey) {
        if (request == null) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "Dữ liệu tạo đánh giá không được để trống.");
        }

        String guestHandle = request.getGuestEligibilityHandle();
        String actorScope = authenticatedMemberId != null
                ? "MEMBER_" + authenticatedMemberId
                : "GUEST_" + (guestHandle != null ? eligibilityService.hashHandle(guestHandle) : "ANON");

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
        if (reviewRepository.existsBySourceTypeAndSourceReference(request.getSourceType(), request.getSourceReference())) {
            throw ReviewManagementException.conflict("REVIEW_ALREADY_EXISTS", "Trải nghiệm/đơn hàng này đã được gửi đánh giá trước đó.");
        }

        OwnerType ownerType;
        String ownerReference;
        String maskedDisplayName;
        // The package of the attended workshop, kept with the review so it can be shown and filtered by package.
        Long packageId = null;
        String packageName = null;

        if (authenticatedMemberId != null) {
            ownerType = OwnerType.MEMBER;
            ownerReference = String.valueOf(authenticatedMemberId);
            // The reviewer's name on the account, partly masked; or anonymous when they chose so. Never the account number.
            maskedDisplayName = wantsAnonymous(request)
                    ? ReviewMaskingService.ANONYMOUS_DISPLAY_NAME
                    : maskingService.maskMemberDisplayName(authenticatedMemberId,
                            accountFacade == null ? null : accountFacade.findAccountFullName(authenticatedMemberId).orElse(null));

            // Verify eligibility
            if (request.getSourceType() == SourceType.WORKSHOP_ATTENDANCE) {
                Optional<EligibleSourceItem> itemOpt = workshopAttendancePort.verifySource(
                        request.getSourceReference(), authenticatedMemberId, null, null);
                if (itemOpt.isEmpty()) {
                    throw ReviewManagementException.unprocessable("REVIEW_SOURCE_NOT_ELIGIBLE", "Vé tham gia workshop không hợp lệ hoặc chưa điểm danh.");
                }
                EligibleSourceItem item = itemOpt.get();
                if (request.getTargetType() != TargetType.WORKSHOP_SESSION || !request.getTargetReference().equals(item.getTargetReference())) {
                    throw ReviewManagementException.unprocessable("REVIEW_TARGET_MISMATCH", "Target của đánh giá không khớp với buổi workshop đã tham dự.");
                }
                packageId = item.getPackageId();
                packageName = item.getTargetName();
            } else if (request.getSourceType() == SourceType.RETAIL_ORDER_LINE) {
                Optional<EligibleSourceItem> itemOpt = retailFulfilmentPort.verifySource(
                        request.getSourceReference(), authenticatedMemberId, null, null);
                if (itemOpt.isEmpty()) {
                    throw ReviewManagementException.unprocessable("REVIEW_SOURCE_NOT_ELIGIBLE", "Mục đơn hàng bán lẻ không hợp lệ hoặc chưa được hoàn tất nhận hàng.");
                }
                EligibleSourceItem item = itemOpt.get();
                if (request.getTargetType() != TargetType.RETAIL_PRODUCT || !request.getTargetReference().equals(item.getTargetReference())) {
                    throw ReviewManagementException.unprocessable("REVIEW_TARGET_MISMATCH", "Target của đánh giá không khớp với sản phẩm bán lẻ trong đơn hàng.");
                }
            } else {
                throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "Loại nguồn đánh giá không được hỗ trợ.");
            }
        } else {
            // Guest submission
            if (guestHandle == null || guestHandle.isBlank()) {
                throw ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED", "Yêu cầu mã guestHandle hợp lệ khi chưa đăng nhập.");
            }
            GuestEligibilityHandle handle = eligibilityService.validateGuestHandle(
                    guestHandle, request.getSourceType(), request.getSourceReference());
            ownerType = OwnerType.GUEST;
            ownerReference = handle.getHandleHash();
            maskedDisplayName = wantsAnonymous(request)
                    ? ReviewMaskingService.ANONYMOUS_DISPLAY_NAME
                    : handle.getContactValueMasked();

            Optional<EligibleSourceItem> itemOpt;
            if (request.getSourceType() == SourceType.WORKSHOP_ATTENDANCE) {
                itemOpt = workshopAttendancePort.verifySource(request.getSourceReference());
            } else if (request.getSourceType() == SourceType.RETAIL_ORDER_LINE) {
                itemOpt = retailFulfilmentPort.verifySource(request.getSourceReference());
            } else {
                itemOpt = Optional.empty();
            }
            if (itemOpt.isEmpty()) {
                // Kiểm tra trực tiếp qua verifySource với contactType và null value (adapter hỗ trợ filter bằng reference)
                throw ReviewManagementException.unprocessable("REVIEW_SOURCE_NOT_ELIGIBLE", "Source không hợp lệ hoặc chưa đủ điều kiện review.");
            }
            EligibleSourceItem item = itemOpt.get();
            TargetType expectedTarget = request.getSourceType() == SourceType.WORKSHOP_ATTENDANCE
                    ? TargetType.WORKSHOP_SESSION : TargetType.RETAIL_PRODUCT;
            if (request.getTargetType() != expectedTarget || !request.getTargetReference().equals(item.getTargetReference())) {
                throw ReviewManagementException.unprocessable("REVIEW_TARGET_MISMATCH", "Target của đánh giá không khớp với source.");
            }
            if (request.getSourceType() == SourceType.WORKSHOP_ATTENDANCE) {
                packageId = item.getPackageId();
                packageName = item.getTargetName();
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
                .packageId(packageId)
                .packageName(packageName)
                .rating(request.getRating())
                .bodyText(request.getBodyText() != null ? request.getBodyText().trim() : null)
                .status(ReviewStatus.PUBLIC)
                // No version here: the id is assigned by us, so a non-null @Version would make Spring Data call merge()
                // on a row that does not exist yet and fail with an optimistic-lock error. Hibernate sets version 0.
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

        if (idempotencyService != null) {
            idempotencyService.recordPending(idempotencyKey, "CREATE_REVIEW", actorScope,
                    idempotencyService.computeHash(request));
        }

        try {
            reviewRepository.save(review);
            revisionRepository.save(revision);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate review detected for source {}:{}", request.getSourceType(), request.getSourceReference());
            throw ReviewManagementException.conflict("REVIEW_ALREADY_EXISTS", "Trải nghiệm này đã được gửi đánh giá.");
        }

        ReviewDto responseDto = mapToReviewDto(review, null, true);

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
            if (guestHandle == null || guestHandle.isBlank()) {
                throw ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED", "Yêu cầu mã guestHandle hợp lệ để chỉnh sửa đánh giá khách vãng lai.");
            }
            GuestEligibilityHandle handle = eligibilityService.validateGuestHandle(
                    guestHandle, review.getSourceType(), review.getSourceReference());
            if (!handle.getHandleHash().equals(review.getOwnerReference())) {
                throw ReviewManagementException.forbidden("REVIEW_NOT_AUTHORIZED", "Mã guestHandle không khớp với chủ sở hữu đánh giá.");
            }
        }

        // Optimistic locking
        if (expectedVersion == null || !review.getVersion().equals(expectedVersion)) {
            throw ReviewManagementException.conflict("REVIEW_STATE_CONFLICT", "Phiên bản dữ liệu không khớp (Optimistic Lock Conflict). Vui lòng tải lại trang.");
        }

        String actorScope = authenticatedMemberId != null
                ? "MEMBER_" + authenticatedMemberId
                : "GUEST_" + (guestHandle != null ? eligibilityService.hashHandle(guestHandle) : "ANON");
        String requestHash = idempotencyService != null ? idempotencyService.computeHash(request) : null;
        Optional<ReviewDto> cached = idempotencyService != null ? idempotencyService.findCompletedResponse(
                idempotencyKey, actorScope, requestHash, ReviewDto.class) : Optional.empty();
        if (cached.isPresent()) return cached.get();
        if (idempotencyService != null) idempotencyService.recordPending(idempotencyKey, "EDIT_REVIEW", actorScope, requestHash);

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
        ReviewDto result = mapToReviewDto(review, replyDto, true);
        if (idempotencyService != null) idempotencyService.recordSuccess(idempotencyKey, actorScope, result);
        return result;
    }

    @Transactional(readOnly = true)
    public ReviewDto getReviewDetail(String reviewId, CurrentAccountActor actor) {
        if (reviewId == null || reviewId.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "ID đánh giá không hợp lệ.");
        }
        Review review = reviewRepository.findById(reviewId.trim())
                .orElseThrow(() -> ReviewManagementException.notFound("REVIEW_NOT_FOUND", "Không tìm thấy đánh giá với ID: " + reviewId));

        boolean owner = actor != null && actor.role() == ActorRole.MEMBER
                && review.getOwnerType() == OwnerType.MEMBER
                && String.valueOf(actor.accountId()).equals(review.getOwnerReference());
        if (review.getStatus() != ReviewStatus.PUBLIC && !owner) {
            throw ReviewManagementException.notFound("REVIEW_NOT_FOUND", "Không tìm thấy review.");
        }
        ReplyDto replyDto = replyRepository.findByReviewId(reviewId)
                .map(reply -> mapToReplyDto(reply, owner)).orElse(null);
        return mapToReviewDto(review, replyDto, owner);
    }

    @Deprecated
    public ReviewDto getReviewDetail(String reviewId) {
        return getReviewDetail(reviewId, null);
    }

    public ReviewDto mapToReviewDto(Review review, ReplyDto reply) {
        return mapToReviewDto(review, reply, false);
    }

    public ReviewDto mapToReviewDto(Review review, ReplyDto reply, boolean includeOwnerData) {
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
                .version(review.getVersion())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .reply(reply)
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

