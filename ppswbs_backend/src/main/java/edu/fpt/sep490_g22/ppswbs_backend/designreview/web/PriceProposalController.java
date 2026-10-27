package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.PriceSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/price-proposals")
@RequiredArgsConstructor
@Slf4j
public class PriceProposalController {

    private final PriceSuggestionService priceSuggestionService;

    @PostMapping("/{proposalId}/ai-suggestions")
    public ResponseEntity<PriceSuggestionService.PriceSuggestionViewDto> requestPriceSuggestion(
            @PathVariable("proposalId") String proposalId,
            @RequestParam(value = "targetType", defaultValue = "WORKSHOP_PACKAGE") String targetType,
            @RequestBody(required = false) String bodyContext,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Yêu cầu xác thực tài khoản.");
        }

        String managerUid = authentication.getPrincipal().toString();
        PriceSuggestionService.PriceSuggestionViewDto suggestion = priceSuggestionService.requestSuggestion(
                proposalId,
                targetType,
                bodyContext,
                managerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(suggestion);
    }

    @GetMapping("/{proposalId}/ai-suggestions")
    public ResponseEntity<PriceSuggestionService.PriceSuggestionViewDto> getPriceSuggestion(
            @PathVariable("proposalId") String proposalId,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Yêu cầu xác thực tài khoản.");
        }

        String callerUid = authentication.getPrincipal().toString();
        PriceSuggestionService.PriceSuggestionViewDto view = priceSuggestionService.getSuggestion(
                proposalId,
                callerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(view);
    }

    @PostMapping(value = {"/{proposalId}/ai-suggestions/decisions", "/{proposalId}/decisions"})
    public ResponseEntity<PriceSuggestionService.PriceSuggestionViewDto> recordManagerDecision(
            @PathVariable("proposalId") String proposalId,
            @RequestBody PriceSuggestionService.PriceDecisionRequestDto decisionRequest,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Yêu cầu xác thực tài khoản.");
        }

        String managerUid = authentication.getPrincipal().toString();
        PriceSuggestionService.PriceSuggestionViewDto updated = priceSuggestionService.recordManagerDecision(
                proposalId,
                decisionRequest,
                managerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(updated);
    }
}
