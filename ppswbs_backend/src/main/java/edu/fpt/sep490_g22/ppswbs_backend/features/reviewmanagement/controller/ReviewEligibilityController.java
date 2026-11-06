package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.EligibleSourceListResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.GuestEligibilityLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewEligibilityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReviewEligibilityController {

    private final ReviewEligibilityService eligibilityService;

    @PostMapping("/api/v1/review-eligibility/lookup")
    public ResponseEntity<EligibleSourceListResponse> lookupGuestEligibility(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody GuestEligibilityLookupRequest request) {
        EligibleSourceListResponse response = eligibilityService.lookupGuestEligibility(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/reviews/eligible-sources")
    public ResponseEntity<EligibleSourceListResponse> getMemberEligibleSources() {
        CurrentAccountActor actor = ActorContext.getCurrentActor()
                .orElseThrow(() -> ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED", "Yêu cầu đăng nhập tài khoản hội viên để lấy danh sách mục đủ điều kiện."));
        if (actor.role() != ActorRole.MEMBER) {
            throw ReviewManagementException.forbidden("REVIEW_NOT_AUTHORIZED", "Chỉ Member mới được xem nguồn review của mình.");
        }

        EligibleSourceListResponse response = eligibilityService.getMemberEligibleSources(actor.accountId());
        return ResponseEntity.ok(response);
    }
}

