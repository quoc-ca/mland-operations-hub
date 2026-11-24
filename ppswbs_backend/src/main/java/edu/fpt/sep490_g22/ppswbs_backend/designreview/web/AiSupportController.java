package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.AiSupportService;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai-support")
@RequiredArgsConstructor
@Slf4j
public class AiSupportController {

    private final AiSupportService aiSupportService;

    @PostMapping("/messages")
    public ResponseEntity<DesignReviewDtos.AiSupportResponse> askAiSupport(
            @Valid @RequestBody DesignReviewDtos.AiSupportRequest request,
            @RequestHeader(value = "X-Guest-AI-Session", required = false) String guestSessionHeader,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        Long memberId = null;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CurrentAccountActor actor) {
            if (actor.role() == ActorRole.MEMBER && actor.status() == ActorStatus.ACTIVE) {
                memberId = actor.accountId();
            } else {
                throw DesignReviewException.forbidden("AI_ACTOR_NOT_ALLOWED",
                        "Nhân viên và quản trị viên không thuộc nhóm sử dụng Basic AI Support.");
            }
        }

        String clientIp = servletRequest.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = servletRequest.getRemoteAddr();
        }

        String correlationId = CorrelationIdFilter.current(servletRequest);
        AiSupportService.SupportResponseDto response = aiSupportService.askSupport(request.getMessage(), memberId,
                guestSessionHeader, clientIp, correlationId);

        HttpHeaders headers = new HttpHeaders();
        if (response.quota() != null) {
            headers.add("X-AI-Quota-Limit", String.valueOf(response.quota().getLimit()));
            headers.add("X-AI-Quota-Remaining", String.valueOf(response.quota().getRemaining()));
            headers.add("X-AI-Quota-Reset-At", response.quota().getResetAt());
        }
        if (response.guestSessionToken() != null && !response.guestSessionToken().isBlank()) {
            headers.add("X-Guest-AI-Session", response.guestSessionToken());
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(DesignReviewDtos.AiSupportResponse.builder()
                        .answer(response.message())
                        .advisory(true)
                        .scope(response.scope())
                        .correlationId(response.providerCorrelationId())
                        .quota(response.quota())
                        .guestSessionToken(response.guestSessionToken())
                        .build());
    }

    @GetMapping("/quota")
    public ResponseEntity<DesignReviewDtos.AiQuotaDto> getQuota(
            @RequestHeader(value = "X-Guest-AI-Session", required = false) String guestSessionHeader,
            Authentication authentication) {
        Long memberId = null;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CurrentAccountActor actor) {
            if (actor.role() == ActorRole.MEMBER && actor.status() == ActorStatus.ACTIVE) {
                memberId = actor.accountId();
            }
        }
        DesignReviewDtos.AiQuotaDto quota = aiSupportService.peekQuota(memberId, guestSessionHeader);
        return ResponseEntity.ok(quota);
    }
}
