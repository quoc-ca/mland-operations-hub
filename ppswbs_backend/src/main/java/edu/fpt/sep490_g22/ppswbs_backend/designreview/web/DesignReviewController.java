package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.HumanReviewService;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewDecision;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/design-review-requests")
@RequiredArgsConstructor
@Slf4j
public class DesignReviewController {

    private final HumanReviewService humanReviewService;

    @GetMapping
    public ResponseEntity<List<DesignAnalysisService.DesignRequestViewDto>> getReviewQueue(
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Yêu cầu xác thực tài khoản.");
        }

        String callerUid = authentication.getPrincipal().toString();
        List<DesignAnalysisService.DesignRequestViewDto> queue = humanReviewService.getReviewQueue(
                callerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(queue);
    }

    @PostMapping("/{reviewRequestId}/decisions")
    public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> recordDecision(
            @PathVariable("reviewRequestId") String reviewRequestId,
            @Valid @RequestBody DesignReviewDtos.ReviewDecisionRequest body,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Yêu cầu xác thực tài khoản.");
        }

        ReviewDecision decision;
        try {
            decision = ReviewDecision.valueOf(body.getDecision().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Quyết định thẩm duyệt không hợp lệ. Phải là ACCEPTED, REJECTED, hoặc CLARIFICATION_REQUIRED.");
        }

        String callerUid = authentication.getPrincipal().toString();
        DesignAnalysisService.DesignRequestViewDto result = humanReviewService.recordDecision(
                reviewRequestId,
                decision,
                body.getDesignVersion(),
                body.getReason(),
                callerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(result);
    }
}
