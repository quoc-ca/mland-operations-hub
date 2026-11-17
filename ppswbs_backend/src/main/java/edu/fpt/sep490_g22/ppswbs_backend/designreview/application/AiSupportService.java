package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.AiTextInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiSupportService {

    private final AiTextSupportPort aiTextSupportPort;
    private final AiTextInteractionRepository aiTextInteractionRepository;

    private static final int MAX_PROMPT_LENGTH = 2000;

    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?previous", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("publish\\s+(this\\s+)?price", Pattern.CASE_INSENSITIVE),
            Pattern.compile("approve\\s+(this\\s+)?(order|booking|request)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bỏ\\s+qua\\s+chỉ\\s+dẫn", Pattern.CASE_INSENSITIVE),
            Pattern.compile("phê\\s+duyệt\\s+đơn", Pattern.CASE_INSENSITIVE),
            Pattern.compile("duyệt\\s+yêu\\s+cầu", Pattern.CASE_INSENSITIVE),
            Pattern.compile("công\\s+bố\\s+giá", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> FORBIDDEN_CONTEXT_PATTERNS = List.of(
            Pattern.compile("(custom\\s+order|đơn\\s+gia\\s+công|custom\\s+manufacturing)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(thanh\\s+toán\\s+giúp|pay\\s+now|confirm\\s+payment)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(credit\\s+card|mật\\s+khẩu|password|firebase\\s+token)", Pattern.CASE_INSENSITIVE)
    );

    public record SupportResponseDto(
            String message,
            String scope,
            String providerCorrelationId
    ) {}

    @Transactional
    public SupportResponseDto askSupport(String message, Long memberId, String correlationId) {
        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId : UUID.randomUUID().toString();

        if (message == null || message.trim().isEmpty()) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Nội dung câu hỏi không được để trống.");
        }

        String sanitized = message.trim();
        if (sanitized.length() > MAX_PROMPT_LENGTH) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Nội dung câu hỏi vượt quá độ dài cho phép (tối đa 2.000 ký tự).");
        }

        // Screen for prompt injection
        for (Pattern p : PROMPT_INJECTION_PATTERNS) {
            if (p.matcher(sanitized).find()) {
                log.warn("Prompt injection attempt detected: correlationId={}", effectiveCorrelationId);
                recordFailedInteraction(sanitized, memberId, "AI_PROMPT_INJECTION_DETECTED", effectiveCorrelationId);
                throw DesignReviewException.unprocessable("AI_PROMPT_INJECTION_DETECTED", "Nội dung yêu cầu vi phạm chính sách an toàn của hệ thống.");
            }
        }

        // Screen for forbidden business contexts
        for (Pattern p : FORBIDDEN_CONTEXT_PATTERNS) {
            if (p.matcher(sanitized).find()) {
                log.warn("Forbidden business context requested: correlationId={}", effectiveCorrelationId);
                recordFailedInteraction(sanitized, memberId, "AI_CONTEXT_NOT_ALLOWED", effectiveCorrelationId);
                throw DesignReviewException.unprocessable("AI_CONTEXT_NOT_ALLOWED", "Trợ lý AI chỉ giải đáp thông tin công khai và không xử lý thanh toán hay gia công riêng.");
            }
        }

        // Call Port
        AiTextSupportPort.TextSupportResult result = aiTextSupportPort.generateSupportResponse(sanitized, effectiveCorrelationId);

        if (!result.success()) {
            recordFailedInteraction(sanitized, memberId, result.failureCode(), effectiveCorrelationId);
            if ("AI_UNAVAILABLE".equals(result.failureCode())) {
                throw DesignReviewException.serviceUnavailable("AI_UNAVAILABLE", "Dịch vụ AI tạm thời không phản hồi. Vui lòng thử lại sau.");
            }
            throw DesignReviewException.unprocessable(result.failureCode() != null ? result.failureCode() : "AI_RESPONSE_INVALID", "Không thể xử lý phản hồi từ AI.");
        }

        // Persist interaction with 30-day retention
        AiTextInteraction interaction = AiTextInteraction.builder()
                .actorScope(memberId != null ? ActorScope.MEMBER : ActorScope.GUEST)
                .memberId(memberId)
                .promptText(sanitized)
                .responseText(result.responseText())
                .scope(result.scope())
                .providerCorrelationId(result.providerCorrelationId())
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(30L * 24 * 3600))
                .build();
        aiTextInteractionRepository.save(interaction);

        return new SupportResponseDto(result.responseText(), result.scope().name(), result.providerCorrelationId());
    }

    private void recordFailedInteraction(String prompt, Long memberId, String failureCode, String correlationId) {
        try {
            AiTextInteraction interaction = AiTextInteraction.builder()
                    .actorScope(memberId != null ? ActorScope.MEMBER : ActorScope.GUEST)
                    .memberId(memberId)
                    .promptText(prompt)
                    .scope(AiScope.OUT_OF_SCOPE)
                    .safeFailureCode(failureCode)
                    .providerCorrelationId(correlationId)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(30L * 24 * 3600))
                    .build();
            aiTextInteractionRepository.save(interaction);
        } catch (Exception e) {
            log.error("Failed to record failed AI text interaction: {}", e.getMessage());
        }
    }
}
