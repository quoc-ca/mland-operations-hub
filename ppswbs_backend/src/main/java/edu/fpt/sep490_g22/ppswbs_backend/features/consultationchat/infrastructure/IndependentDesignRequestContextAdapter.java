package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.facade.DesignRequestConsultationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.facade.DesignRequestConsultationOption;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.ContextOptionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.ContextPreviewDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationContextType;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.IndependentDesignRequestContextPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IndependentDesignRequestContextAdapter implements IndependentDesignRequestContextPort {

    private final DesignRequestConsultationFacade designRequestConsultationFacade;

    @Override
    public List<ContextOptionDto> findMemberIndependentDesignRequests(Long memberAccountId) {
        if (memberAccountId == null) return Collections.emptyList();
        List<DesignRequestConsultationOption> options = designRequestConsultationFacade.findMemberRequestsForConsultation(memberAccountId);
        return options.stream().map(this::toDto).toList();
    }

    @Override
    public Optional<ContextOptionDto> validateIndependentDesignRequest(
            Long memberAccountId, String contextReference, Integer contextVersion
    ) {
        return designRequestConsultationFacade.validateMemberRequestForConsultation(memberAccountId, contextReference, contextVersion)
                .map(this::toDto);
    }

    private ContextOptionDto toDto(DesignRequestConsultationOption opt) {
        return ContextOptionDto.builder()
                .contextType(ConsultationContextType.INDEPENDENT_DESIGN_REQUEST)
                .contextReference(opt.requestReference())
                .displayCode(opt.displayCode())
                .displayName(opt.displayName())
                .designVersion(opt.requestVersion())
                .preview(ContextPreviewDto.builder()
                        .imageUrl(opt.previewImageUrl())
                        .summary(opt.previewSummary())
                        .build())
                .build();
    }
}
