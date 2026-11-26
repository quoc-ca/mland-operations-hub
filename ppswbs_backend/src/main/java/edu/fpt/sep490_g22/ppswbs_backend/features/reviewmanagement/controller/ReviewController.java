package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.CreateReviewRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.EditReviewRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewPageDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewPublicReadFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewApplicationService applicationService;
    private final ReviewPublicReadFacade publicReadFacade;

    @PostMapping
    public ResponseEntity<ReviewDto> createReview(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateReviewRequest request) {
        var actor = ActorContext.getCurrentActor()
                .orElseThrow(() -> edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException
                        .unauthorized("REVIEW_NOT_AUTHORIZED", "Yêu cầu đăng nhập tài khoản hội viên để tạo đánh giá."));
        if (actor.role() != ActorRole.MEMBER) {
            throw edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException
                    .forbidden("REVIEW_NOT_AUTHORIZED", "Chỉ Member mới được tạo review.");
        }
        Long memberAccountId = actor.accountId();
        ReviewDto dto = applicationService.createReview(request, memberAccountId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /** Public reviews, newest first. Every filter is optional (contract GET /reviews); no filter lists them all. */
    @GetMapping
    public ResponseEntity<PublicReviewPageDto> getPublicReviews(
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(required = false) String targetReference,
            @RequestParam(required = false) Long packageId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "recent") String sort) {
        PublicReviewPageDto page = publicReadFacade.getPublicReviews(
                new PublicReviewQuery(targetType, targetReference, packageId, rating, cursor, limit, sort));
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewDto> getReviewDetail(@PathVariable String reviewId) {
        ReviewDto dto = applicationService.getReviewDetail(reviewId, ActorContext.getCurrentActor().orElse(null));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{reviewId}/technical-projection")
    public ResponseEntity<edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewTechnicalProjectionDto> getTechnicalProjection(
            @PathVariable String reviewId) {
        var actor = ActorContext.requireCurrentActor();
        if (actor.role() != ActorRole.ADMIN && actor.role() != ActorRole.MANAGER) {
            throw edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException
                    .forbidden("REVIEW_NOT_AUTHORIZED", "Chỉ Admin hoặc Manager mới có quyền xem technical projection của đánh giá.");
        }
        var dto = applicationService.getTechnicalProjection(reviewId);
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{reviewId}")
    public ResponseEntity<ReviewDto> editReview(
            @PathVariable String reviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long expectedVersion,
            @Valid @RequestBody EditReviewRequest request) {
        var actor = ActorContext.getCurrentActor()
                .orElseThrow(() -> edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException
                        .unauthorized("REVIEW_NOT_AUTHORIZED", "Yêu cầu đăng nhập tài khoản hội viên để chỉnh sửa đánh giá."));
        if (actor.role() != ActorRole.MEMBER) {
            throw edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException
                    .forbidden(
                            "REVIEW_NOT_AUTHORIZED", "Chỉ Member mới được chỉnh sửa review sở hữu.");
        }
        Long memberAccountId = actor.accountId();
        ReviewDto dto = applicationService.editReview(reviewId, request, memberAccountId, null, expectedVersion,
                idempotencyKey);
        return ResponseEntity.ok(dto);
    }
}
