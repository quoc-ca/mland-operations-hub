package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.AiSupportService;
import edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberDto;
import edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberFacade;
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
    private final MemberFacade memberFacade;

    @PostMapping("/messages")
    public ResponseEntity<DesignReviewDtos.AiSupportResponse> askAiSupport(
            @Valid @RequestBody DesignReviewDtos.AiSupportRequest request,
            Authentication authentication
    ) {
        Long memberId = null;
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() != null) {
            String uid = authentication.getPrincipal().toString();
            memberId = memberFacade.getMemberByUid(uid).map(MemberDto::getId).orElse(null);
        }

        String correlationId = CorrelationIdFilter.getCurrentCorrelationId();
        AiSupportService.SupportResponseDto response = aiSupportService.askSupport(request.getMessage(), memberId, correlationId);

        return ResponseEntity.ok(DesignReviewDtos.AiSupportResponse.builder()
                .message(response.message())
                .scope(response.scope())
                .providerCorrelationId(response.providerCorrelationId())
                .build());
    }
}
