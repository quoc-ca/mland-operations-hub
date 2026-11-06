package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewModerationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewReplyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReviewModerationController {

    private final ReviewModerationService moderationService;
    private final ReviewReplyService replyService;

    @PatchMapping("/api/v1/reviews/{reviewId}/visibility")
    public ResponseEntity<ModerationItemDto> changeVisibility(
            @PathVariable String reviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long expectedVersion,
            @Valid @RequestBody VisibilityCommandRequest request) {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        ModerationItemDto dto = moderationService.changeVisibility(
                reviewId, request, actor.accountId(), actor.role() != null ? actor.role().name() : "UNKNOWN",
                expectedVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/api/v1/review-moderation")
    public ResponseEntity<ModerationPageDto> getModerationQueue(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(required = false) String targetReference,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        String role = actor.role() != null ? actor.role().name() : "";
        if (!"STAFF".equalsIgnoreCase(role) && !"MANAGER".equalsIgnoreCase(role)) {
            throw ReviewManagementException.forbidden("REVIEW_NOT_AUTHORIZED", "Chỉ nhân viên hoặc quản lý mới có quyền xem danh sách kiểm duyệt.");
        }

        ModerationPageDto page = moderationService.getModerationQueue(status, targetType, targetReference, pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/api/v1/reviews/{reviewId}/reply")
    public ResponseEntity<ReplyDto> createReply(
            @PathVariable String reviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReplyCommandRequest request) {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        String role = actor.role() != null ? actor.role().name() : "";
        if (!"MANAGER".equalsIgnoreCase(role)) {
            throw ReviewManagementException.forbidden("REVIEW_NOT_AUTHORIZED", "Chỉ Quản lý (Manager) mới có quyền tạo phản hồi chính thức.");
        }

        ReplyDto reply = replyService.createReply(reviewId, request, actor.accountId(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(reply);
    }

    @PatchMapping("/api/v1/reviews/{reviewId}/reply")
    public ResponseEntity<ReplyDto> editReply(
            @PathVariable String reviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long expectedVersion,
            @Valid @RequestBody ReplyCommandRequest request) {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        String role = actor.role() != null ? actor.role().name() : "";
        if (!"MANAGER".equalsIgnoreCase(role)) {
            throw ReviewManagementException.forbidden("REVIEW_NOT_AUTHORIZED", "Chỉ Quản lý (Manager) mới có quyền chỉnh sửa phản hồi chính thức.");
        }

        ReplyDto reply = replyService.editReply(reviewId, request, actor.accountId(), expectedVersion, idempotencyKey);
        return ResponseEntity.ok(reply);
    }
}

