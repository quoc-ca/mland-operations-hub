package edu.fpt.sep490_g22.ppswbs_backend.designreview.facade;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.PriceSuggestion;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.PriceSuggestionRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PriceSuggestionFacade {

    private final PriceSuggestionRepository priceSuggestionRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceSuggestionSummaryDto {
        private String proposalId;
        private Double suggestedPrice;
        private String suggestionStatus;
        private boolean advisory;
        private String managerDecision;
        private Double finalPrice;
        private java.time.Instant decidedAt;
    }

    @Transactional(readOnly = true)
    public Optional<PriceSuggestionSummaryDto> getLatestSuggestionForProposal(String proposalId) {
        if (proposalId == null || proposalId.isBlank()) {
            return Optional.empty();
        }
        return priceSuggestionRepository.findFirstByProposalIdOrderByCreatedAtDesc(proposalId.trim())
                .map(s -> PriceSuggestionSummaryDto.builder()
                        .proposalId(s.getProposalId())
                        .suggestedPrice(s.getSuggestedPrice())
                        .suggestionStatus(s.getSuggestionStatus().name())
                        .advisory(s.isAdvisory())
                        .managerDecision(s.getManagerDecision() != null ? s.getManagerDecision().name() : null)
                        .finalPrice(s.getFinalPrice())
                        .decidedAt(s.getDecidedAt())
                        .build());
    }
}
