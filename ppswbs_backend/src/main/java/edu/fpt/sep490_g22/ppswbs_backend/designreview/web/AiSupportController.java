package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.AiSupportService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-support")
@RequiredArgsConstructor
@Slf4j
public class AiSupportController {

    private final AiSupportService aiSupportService;

    @PostMapping("/messages")
    public ResponseEntity<DesignReviewDtos.AiSupportResponse> askAiSupport(
            @Valid @RequestBody DesignReviewDtos.AiSupportRequest request,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        Long memberId = null;
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof CurrentAccountActor actor) {
            if (actor.role() == ActorRole.MEMBER && actor.status() == ActorStatus.ACTIVE) {
                memberId = actor.accountId();
            }
        }

        String correlationId = CorrelationIdFilter.current(servletRequest);
        AiSupportService.SupportResponseDto response = aiSupportService.askSupport(request.getMessage(), memberId, correlationId);

        return ResponseEntity.ok(DesignReviewDtos.AiSupportResponse.builder()
                .message(response.message())
                .scope(response.scope())
                .providerCorrelationId(response.providerCorrelationId())
                .build());
    }
}
