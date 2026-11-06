package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.PriceSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/price-proposals")
@RequiredArgsConstructor
@Slf4j
public class PriceProposalController {

    private final PriceSuggestionService priceSuggestionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/{proposalId}/ai-suggestions")
    public ResponseEntity<PriceSuggestionService.PriceSuggestionViewDto> requestPriceSuggestion(
            @PathVariable("proposalId") String proposalId,
            @RequestParam(value = "targetType", defaultValue = "WORKSHOP_PACKAGE") String targetTypeParam,
            @RequestBody(required = false) String bodyContext,
            Authentication authentication
    ) {
        String managerUid = SecurityIdentityExtractor.requireUid(authentication);
        String targetType = targetTypeParam;

        if (bodyContext != null && bodyContext.contains("\"targetType\"")) {
            try {
                JsonNode node = objectMapper.readTree(bodyContext);
                if (node.hasNonNull("targetType")) {
                    targetType = node.get("targetType").asText();
                }
            } catch (Exception ignored) {
            }
        }

        PriceSuggestionService.PriceSuggestionViewDto suggestion = priceSuggestionService.requestSuggestion(
                proposalId,
                targetType,
                bodyContext,
                managerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(suggestion);
    }

    @GetMapping("/{proposalId}/ai-suggestions")
    public ResponseEntity<PriceSuggestionService.PriceSuggestionViewDto> getPriceSuggestion(
            @PathVariable("proposalId") String proposalId,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
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
        String managerUid = SecurityIdentityExtractor.requireUid(authentication);
        PriceSuggestionService.PriceSuggestionViewDto updated = priceSuggestionService.recordManagerDecision(
                proposalId,
                decisionRequest,
                managerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(updated);
    }
}
