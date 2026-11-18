package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationContextType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextOptionDto {
    private ConsultationContextType contextType;
    private String contextReference;
    private String displayCode;
    private String displayName;
    private ContextPreviewDto preview;
    private Integer designVersion;
}
