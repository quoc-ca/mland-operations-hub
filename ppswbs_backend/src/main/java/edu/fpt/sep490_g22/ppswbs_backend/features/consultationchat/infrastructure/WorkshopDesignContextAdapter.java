package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.ContextOptionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.ContextPreviewDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationContextType;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.WorkshopDesignContextPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.facade.WorkshopRingDesignConsultationOption;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.facade.WorkshopRingDesignFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WorkshopDesignContextAdapter implements WorkshopDesignContextPort {

    private final WorkshopRingDesignFacade ringDesignFacade;

    @Override
    public List<ContextOptionDto> findMemberWorkshopDesigns(Long memberAccountId) {
        if (memberAccountId == null) return Collections.emptyList();
        List<WorkshopRingDesignConsultationOption> options = ringDesignFacade.findMemberDesignsForConsultation(memberAccountId);
        return options.stream().map(this::toDto).toList();
    }

    @Override
    public Optional<ContextOptionDto> validateWorkshopDesign(
            Long memberAccountId, String contextReference, Integer contextVersion
    ) {
        return ringDesignFacade.validateMemberDesignForConsultation(memberAccountId, contextReference, contextVersion)
                .map(this::toDto);
    }

    private ContextOptionDto toDto(WorkshopRingDesignConsultationOption opt) {
        return ContextOptionDto.builder()
                .contextType(ConsultationContextType.WORKSHOP_RING_DESIGN)
                .contextReference(opt.designReference())
                .displayCode(opt.displayCode())
                .displayName(opt.displayName())
                .designVersion(opt.designVersion())
                .preview(ContextPreviewDto.builder()
                        .imageUrl(opt.previewImageUrl())
                        .summary(opt.previewSummary())
                        .build())
                .build();
    }
}
