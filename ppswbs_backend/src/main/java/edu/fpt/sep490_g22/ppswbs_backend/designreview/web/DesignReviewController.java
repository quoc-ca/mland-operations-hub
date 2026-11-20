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
    private final DesignAnalysisService designAnalysisService;

    @GetMapping
    public ResponseEntity<DesignReviewDtos.ReviewQueueDto> getReviewQueue(
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        List<DesignAnalysisService.DesignRequestViewDto> queue = humanReviewService.getReviewQueue(
                callerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(DesignReviewDtos.ReviewQueueDto.builder().items(queue).build());
    }

    @GetMapping("/{reviewRequestId}")
    public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> getReviewDetail(
            @PathVariable("reviewRequestId") String reviewRequestId,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        DesignAnalysisService.DesignRequestViewDto view = designAnalysisService.getDesignRequestById(
                reviewRequestId,
                callerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(view);
    }

    @PostMapping("/{reviewRequestId}/decisions")
    public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> recordDecision(
            @PathVariable("reviewRequestId") String reviewRequestId,
            @RequestHeader(value = "If-Match-Version", required = false) Integer ifMatchVersionHeader,
            @Valid @RequestBody DesignReviewDtos.ReviewDecisionRequest body,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);

        ReviewDecision decision;
        try {
            decision = ReviewDecision.valueOf(body.getDecision().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw DesignReviewException.badRequest("DESIGN_REQUEST_INVALID", "Quyết định thẩm duyệt không hợp lệ. Phải là ACCEPTED hoặc REJECTED.");
        }

        Integer version = ifMatchVersionHeader != null ? ifMatchVersionHeader : body.getRequestVersion();

        DesignAnalysisService.DesignRequestViewDto result = humanReviewService.recordDecision(
                reviewRequestId,
                decision,
                version,
                body.getReason(),
                callerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(result);
    }
}
