package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.AiTextInteractionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiSupportService {

    private final AiTextSupportPort aiTextSupportPort;
    private final AiTextInteractionRepository aiTextInteractionRepository;
    private final AiGuestSessionService aiGuestSessionService;
    private final AiPromptQuotaService aiPromptQuotaService;

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
            Pattern.compile("công\\s+bố\\s+giá", Pattern.CASE_INSENSITIVE));

    private static final List<Pattern> FORBIDDEN_CONTEXT_PATTERNS = List.of(
            Pattern.compile("(custom\\s+order|đơn\\s+gia\\s+công|custom\\s+manufacturing)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(thanh\\s+toán\\s+giúp|pay\\s+now|confirm\\s+payment)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(credit\\s+card|mật\\s+khẩu|password|firebase\\s+token)", Pattern.CASE_INSENSITIVE));

    public record SupportResponseDto(
            String message,
            String scope,
            String providerCorrelationId,
            DesignReviewDtos.AiQuotaDto quota,
            String guestSessionToken) {
    }

    @Transactional
    public SupportResponseDto askSupport(String message, Long memberId, String correlationId) {
        return askSupport(message, memberId, null, null, correlationId);
    }

    @Transactional
    public SupportResponseDto askSupport(String message, Long memberId, String guestSessionHeader,
            String clientIp, String correlationId) {
        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();

        // 1. Basic validation (empty or > 2000 chars does NOT consume quota)
        if (message == null || message.trim().isEmpty()) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Nội dung câu hỏi không được để trống.");
        }

        String sanitized = message.trim();
        if (sanitized.length() > MAX_PROMPT_LENGTH) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID",
                    "Nội dung câu hỏi vượt quá độ dài cho phép (tối đa 2.000 ký tự).");
        }

        // 2. Resolve Guest session vs Member
        String actorScope;
        String quotaIdentifier;
        String responseGuestSessionToken = null;

        if (memberId != null) {
            actorScope = "MEMBER";
            quotaIdentifier = String.valueOf(memberId);
        } else {
            actorScope = "GUEST";
            if (guestSessionHeader != null && !guestSessionHeader.trim().isBlank()) {
                Optional<AiGuestSessionService.GuestSessionResult> sessionOpt = aiGuestSessionService
                        .validateAndTouch(guestSessionHeader.trim());
                if (sessionOpt.isEmpty()) {
                    log.warn("Guest provided invalid or expired AI session");
                    throw DesignReviewException.unauthorized("AI_GUEST_SESSION_INVALID",
                            "Phiên AI của khách vãng lai không hợp lệ hoặc đã hết hạn.");
                }
                quotaIdentifier = sessionOpt.get().sessionDigest();
                responseGuestSessionToken = sessionOpt.get().rawSessionToken();
            } else {
                // First guest request without session -> create new session
                AiGuestSessionService.GuestSessionResult newSession = aiGuestSessionService.createGuestSession();
                quotaIdentifier = newSession.sessionDigest();
                responseGuestSessionToken = newSession.rawSessionToken();
            }
        }

        // 3. Atomically consume daily quota
        AiPromptQuotaService.QuotaResult quotaResult;
        try {
            quotaResult = aiPromptQuotaService.consumeQuota(actorScope, quotaIdentifier, effectiveCorrelationId);
        } catch (DesignReviewException ex) {
            throw ex.withGuestSessionToken(responseGuestSessionToken);
        }

        // 4. Screen for prompt injection (consumes quota)
        for (Pattern p : PROMPT_INJECTION_PATTERNS) {
            if (p.matcher(sanitized).find()) {
                log.warn("Prompt injection attempt detected: correlationId={}", effectiveCorrelationId);
                recordFailedInteraction(sanitized, memberId, "AI_PROMPT_INJECTION_DETECTED", effectiveCorrelationId);
                throw withGuestSession(DesignReviewException.unprocessable("AI_PROMPT_INJECTION_DETECTED",
                        "Nội dung yêu cầu vi phạm chính sách an toàn của hệ thống."), responseGuestSessionToken);
            }
        }

        // 5. Screen for forbidden business contexts (consumes quota)
        for (Pattern p : FORBIDDEN_CONTEXT_PATTERNS) {
            if (p.matcher(sanitized).find()) {
                log.warn("Forbidden business context requested: correlationId={}", effectiveCorrelationId);
                recordFailedInteraction(sanitized, memberId, "AI_CONTEXT_NOT_ALLOWED", effectiveCorrelationId);
                throw withGuestSession(DesignReviewException.unprocessable("AI_CONTEXT_NOT_ALLOWED",
                        "Trợ lý AI chỉ giải đáp thông tin công khai và không xử lý thanh toán hay gia công riêng."),
                        responseGuestSessionToken);
            }
        }

        // 6. Call Port
        AiTextSupportPort.TextSupportResult result = aiTextSupportPort.generateSupportResponse(sanitized,
                effectiveCorrelationId);

        if (!result.success()) {
            recordFailedInteraction(sanitized, memberId, result.failureCode(), effectiveCorrelationId);
            if ("AI_UNAVAILABLE".equals(result.failureCode())) {
                throw withGuestSession(
                        DesignReviewException.serviceUnavailable("AI_UNAVAILABLE",
                                "Dịch vụ AI tạm thời không phản hồi. Vui lòng thử lại sau."),
                        responseGuestSessionToken);
            }
            throw withGuestSession(DesignReviewException.unprocessable(
                    result.failureCode() != null ? result.failureCode() : "AI_RESPONSE_INVALID",
                    "Không thể xử lý phản hồi từ AI."), responseGuestSessionToken);
        }

        // 7. Persist interaction with 30-day retention
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

        return new SupportResponseDto(
                result.responseText(),
                result.scope().name(),
                result.providerCorrelationId(),
                quotaResult.toDto(),
                responseGuestSessionToken);
    }

    @Transactional(readOnly = true)
    public DesignReviewDtos.AiQuotaDto peekQuota(Long memberId, String guestSessionHeader) {
        String actorScope;
        String quotaIdentifier;

        if (memberId != null) {
            actorScope = "MEMBER";
            quotaIdentifier = String.valueOf(memberId);
        } else {
            actorScope = "GUEST";
            if (guestSessionHeader != null && !guestSessionHeader.trim().isBlank()) {
                Optional<AiGuestSessionService.GuestSessionResult> sessionOpt = aiGuestSessionService
                        .validateAndTouch(guestSessionHeader.trim());
                if (sessionOpt.isPresent()) {
                    quotaIdentifier = sessionOpt.get().sessionDigest();
                } else {
                    quotaIdentifier = "anon-guest";
                }
            } else {
                quotaIdentifier = "anon-guest";
            }
        }

        return aiPromptQuotaService.peekQuota(actorScope, quotaIdentifier);
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

    private DesignReviewException withGuestSession(DesignReviewException exception, String token) {
        return exception.withGuestSessionToken(token);
    }
}
