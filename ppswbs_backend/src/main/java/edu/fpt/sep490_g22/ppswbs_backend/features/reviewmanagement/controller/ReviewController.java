package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.CreateReviewRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.EditReviewRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewPageDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewPublicReadFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
            @RequestHeader(value = "Guest-Eligibility-Handle", required = false) String guestHandleHeader,
            @Valid @RequestBody CreateReviewRequest request) {
        if (request.getGuestEligibilityHandle() == null && guestHandleHeader != null && !guestHandleHeader.isBlank()) {
            request.setGuestEligibilityHandle(guestHandleHeader.trim());
        }
        var actor = ActorContext.getCurrentActor().orElse(null);
        Long memberAccountId = actor != null && actor.role() == ActorRole.MEMBER ? actor.accountId() : null;
        if (actor != null && actor.role() != ActorRole.MEMBER) {
            throw edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException.forbidden(
                    "REVIEW_NOT_AUTHORIZED", "Chỉ Member mới được tạo review.");
        }
        ReviewDto dto = applicationService.createReview(request, memberAccountId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public ResponseEntity<PublicReviewPageDto> getPublicReviews(
            @RequestParam TargetType targetType,
            @RequestParam String targetReference,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PublicReviewPageDto page = publicReadFacade.getPublicReviews(targetType, targetReference, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewDto> getReviewDetail(@PathVariable String reviewId) {
        ReviewDto dto = applicationService.getReviewDetail(reviewId, ActorContext.getCurrentActor().orElse(null));
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{reviewId}")
    public ResponseEntity<ReviewDto> editReview(
            @PathVariable String reviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long expectedVersion,
            @RequestHeader(value = "Guest-Eligibility-Handle", required = false) String guestHandle,
            @Valid @RequestBody EditReviewRequest request) {
        var actor = ActorContext.getCurrentActor().orElse(null);
        if (actor != null && actor.role() != ActorRole.MEMBER) {
            throw edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException.forbidden(
                    "REVIEW_NOT_AUTHORIZED", "Chỉ Member mới được chỉnh sửa review sở hữu.");
        }
        Long memberAccountId = actor != null ? actor.accountId() : null;
        ReviewDto dto = applicationService.editReview(reviewId, request, memberAccountId, guestHandle, expectedVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }
}

