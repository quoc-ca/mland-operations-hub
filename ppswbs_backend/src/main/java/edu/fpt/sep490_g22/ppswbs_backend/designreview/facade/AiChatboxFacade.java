package edu.fpt.sep490_g22.ppswbs_backend.designreview.facade;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.AiSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiChatboxFacade {

    private final AiSupportService aiSupportService;

    public AiSupportService.SupportResponseDto askPublicSupport(String prompt, Long memberId, String correlationId) {
        return aiSupportService.askSupport(prompt, memberId, correlationId);
    }
}
