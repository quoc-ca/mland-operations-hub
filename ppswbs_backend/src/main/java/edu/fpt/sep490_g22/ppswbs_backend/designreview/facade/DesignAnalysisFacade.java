package edu.fpt.sep490_g22.ppswbs_backend.designreview.facade;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestRepository;
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
public class DesignAnalysisFacade {

    private final DesignRequestRepository requestRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DesignRequestSummaryDto {
        private String designRequestId;
        private String requestReference;
        private Long ownerAccountId;
        private Integer designVersion;
        private String analysisStatus;
        private String reviewStatus;
        private Long estimatedValue;
        private String nextAction;
    }

    @Transactional(readOnly = true)
    public Optional<DesignRequestSummaryDto> getDesignRequestSummary(String designRequestId) {
        if (designRequestId == null || designRequestId.isBlank()) {
            return Optional.empty();
        }
        return requestRepository.findById(designRequestId.trim())
                .map(this::toSummaryDto);
    }

    private DesignRequestSummaryDto toSummaryDto(DesignRequest req) {
        return DesignRequestSummaryDto.builder()
                .designRequestId(req.getId())
                .requestReference(req.getRequestReference())
                .ownerAccountId(req.getOwnerAccountId())
                .designVersion(req.getRequestVersion())
                .analysisStatus(req.getAnalysisStatus().name())
                .reviewStatus(req.getReviewStatus().name())
                .estimatedValue(req.getEstimatedValue())
                .nextAction(req.getNextAction())
                .build();
    }
}
