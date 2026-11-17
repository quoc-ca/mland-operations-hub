package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.PriceSuggestionRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceSuggestionService {

    private final AiPriceSuggestionPort priceSuggestionPort;
    private final PriceSuggestionRepository priceSuggestionRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceSuggestionViewDto {
        private Long id;
        private String proposalId;
        private String targetType;
        private boolean advisory;
        private String suggestionStatus;
        private Double suggestedPrice;
        private String explanation;
        private String providerCorrelationId;
        private String managerDecision;
        private Double finalPrice;
        private String requestedBy;
        private Instant createdAt;
        private Instant decidedAt;
        private Boolean technicalAuditEvidenceOnly;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceDecisionRequestDto {
        private String decision; // ACCEPTED, ADJUSTED, REJECTED, MANUAL_FALLBACK
        private Double finalPrice;
        private String note;
    }

    @Transactional
    public PriceSuggestionViewDto requestSuggestion(
            String proposalId,
            String targetTypeStr,
            String pricingContextJson,
            String managerUid,
            Collection<? extends GrantedAuthority> authorities
    ) {
        log.info("Requesting advisory price suggestion: proposalId={}, targetType={}, managerUid={}",
                proposalId, targetTypeStr, managerUid);

        // 1. Role Authorization: Manager (Owner) only
        if (!DesignReviewPermissions.isManager(authorities)) {
            throw DesignReviewException.forbidden("FORBIDDEN_ROLE", "Chỉ Quản lý (Owner/Manager) mới có quyền yêu cầu gợi ý giá AI.");
        }

        if (proposalId == null || proposalId.isBlank()) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Mã đề xuất giá không hợp lệ.");
        }

        // 2. Validate target type: Only new workshop package or retail product
        TargetType targetType;
        try {
            targetType = TargetType.valueOf(targetTypeStr != null ? targetTypeStr.toUpperCase() : "WORKSHOP_PACKAGE");
        } catch (IllegalArgumentException e) {
            throw DesignReviewException.unprocessable("AI_FEATURE_NOT_APPLICABLE", "Chỉ hỗ trợ gợi ý giá cho gói workshop mới hoặc sản phẩm bán lẻ mới.");
        }

        String correlationId = UUID.randomUUID().toString();

        // 3. Call Port
        AiPriceSuggestionPort.PriceSuggestionResult result = priceSuggestionPort.suggestPrice(
                proposalId, targetType, pricingContextJson, correlationId
        );

        SuggestionStatus status = result.success() ? SuggestionStatus.SUCCEEDED : SuggestionStatus.FAILED;

        // 4. Record Price Suggestion Entity
        PriceSuggestion suggestion = PriceSuggestion.builder()
                .proposalId(proposalId)
                .targetType(targetType)
                .advisory(true)
                .suggestionStatus(status)
                .suggestedPrice(result.suggestedPrice())
                .explanation(result.explanation())
                .providerCorrelationId(result.providerCorrelationId())
                .requestedBy(managerUid)
                .createdAt(Instant.now())
                .build();
        priceSuggestionRepository.save(suggestion);

        if (!result.success()) {
            throw DesignReviewException.serviceUnavailable("AI_UNAVAILABLE", "Không thể lấy gợi ý giá từ AI lúc này. Vui lòng tự nhập giá thủ công.");
        }

        return mapToManagerView(suggestion);
    }

    @Transactional(readOnly = true)
    public PriceSuggestionViewDto getSuggestion(
            String proposalId,
            String callerUid,
            Collection<? extends GrantedAuthority> authorities
    ) {
        if (proposalId == null || proposalId.isBlank()) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Mã đề xuất giá không hợp lệ.");
        }

        PriceSuggestion suggestion = priceSuggestionRepository.findFirstByProposalIdOrderByCreatedAtDesc(proposalId.trim())
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đề xuất giá cho proposalId: " + proposalId));

        if (!DesignReviewPermissions.canViewPriceSuggestion(authorities)) {
            // Staff, Guest, Member are strictly forbidden from viewing internal price suggestions (spec.md lines 297-300)
            throw DesignReviewException.forbidden("FORBIDDEN_ROLE", "Nhân viên và khách hàng không có quyền truy cập đề xuất định giá.");
        }

        if (DesignReviewPermissions.isAdmin(authorities) && !DesignReviewPermissions.isManager(authorities)) {
            // Admin only sees approved technical/audit evidence (spec.md lines 298-300, FR-04-029)
            return mapToAdminTechnicalView(suggestion);
        }

        return mapToManagerView(suggestion);
    }

    @Transactional
    public PriceSuggestionViewDto recordManagerDecision(
            String proposalId,
            PriceDecisionRequestDto request,
            String managerUid,
            Collection<? extends GrantedAuthority> authorities
    ) {
        log.info("Recording manager price decision: proposalId={}, managerUid={}", proposalId, managerUid);

        // 1. Role Authorization: Manager (Owner) only
        if (!DesignReviewPermissions.canDecidePriceSuggestion(authorities)) {
            throw DesignReviewException.forbidden("FORBIDDEN_ROLE", "Chỉ Quản lý (Owner/Manager) mới có quyền đưa ra quyết định định giá.");
        }

        if (proposalId == null || proposalId.isBlank()) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Mã đề xuất giá không hợp lệ.");
        }

        if (request == null || request.getDecision() == null || request.getDecision().isBlank()) {
            throw DesignReviewException.badRequest("PRICE_SUGGESTION_INVALID", "Quyết định của quản lý không được để trống.");
        }

        ManagerDecision decision;
        try {
            decision = ManagerDecision.valueOf(request.getDecision().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw DesignReviewException.badRequest("PRICE_SUGGESTION_INVALID",
                    "Quyết định không hợp lệ. Phải là một trong: ACCEPTED, ADJUSTED, REJECTED, MANUAL_FALLBACK.");
        }

        PriceSuggestion suggestion = priceSuggestionRepository.findFirstByProposalIdOrderByCreatedAtDesc(proposalId.trim())
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đề xuất giá cho proposalId: " + proposalId));

        Double finalPrice = request.getFinalPrice();

        if (decision == ManagerDecision.ACCEPTED) {
            if (finalPrice == null) {
                finalPrice = suggestion.getSuggestedPrice();
            }
            if (finalPrice == null || finalPrice <= 0) {
                throw DesignReviewException.badRequest("PRICE_SUGGESTION_INVALID", "Giá chấp thuận không hợp lệ (phải > 0).");
            }
        } else if (decision == ManagerDecision.ADJUSTED || decision == ManagerDecision.MANUAL_FALLBACK) {
            if (finalPrice == null || finalPrice <= 0) {
                throw DesignReviewException.badRequest("PRICE_SUGGESTION_INVALID",
                        "Vui lòng cung cấp giá cuối cùng hợp lệ (> 0) cho quyết định " + decision.name() + ".");
            }
        } else if (decision == ManagerDecision.REJECTED) {
            finalPrice = null;
        }

        suggestion.setManagerDecision(decision);
        suggestion.setFinalPrice(finalPrice);
        suggestion.setDecidedAt(Instant.now());
        priceSuggestionRepository.save(suggestion);

        return mapToManagerView(suggestion);
    }

    private PriceSuggestionViewDto mapToManagerView(PriceSuggestion s) {
        return PriceSuggestionViewDto.builder()
                .id(s.getId())
                .proposalId(s.getProposalId())
                .targetType(s.getTargetType() != null ? s.getTargetType().name() : null)
                .advisory(s.isAdvisory())
                .suggestionStatus(s.getSuggestionStatus() != null ? s.getSuggestionStatus().name() : null)
                .suggestedPrice(s.getSuggestedPrice())
                .explanation(s.getExplanation())
                .providerCorrelationId(s.getProviderCorrelationId())
                .managerDecision(s.getManagerDecision() != null ? s.getManagerDecision().name() : null)
                .finalPrice(s.getFinalPrice())
                .requestedBy(s.getRequestedBy())
                .createdAt(s.getCreatedAt())
                .decidedAt(s.getDecidedAt())
                .technicalAuditEvidenceOnly(false)
                .build();
    }

    private PriceSuggestionViewDto mapToAdminTechnicalView(PriceSuggestion s) {
        return PriceSuggestionViewDto.builder()
                .id(s.getId())
                .proposalId(s.getProposalId())
                .targetType(s.getTargetType() != null ? s.getTargetType().name() : null)
                .advisory(s.isAdvisory())
                .suggestionStatus(s.getSuggestionStatus() != null ? s.getSuggestionStatus().name() : null)
                .suggestedPrice(null)  // Spec invariant: Admin cannot see business price suggestion
                .explanation(null)     // Spec invariant: Admin cannot see business pricing rationale
                .providerCorrelationId(s.getProviderCorrelationId())
                .managerDecision(null) // Spec invariant: Admin cannot see manager commercial decision
                .finalPrice(null)      // Spec invariant: Admin cannot see commercial final price
                .requestedBy(s.getRequestedBy())
                .createdAt(s.getCreatedAt())
                .decidedAt(s.getDecidedAt())
                .technicalAuditEvidenceOnly(true)
                .build();
    }
}
