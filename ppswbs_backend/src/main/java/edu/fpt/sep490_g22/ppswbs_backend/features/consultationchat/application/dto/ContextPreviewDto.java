package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextPreviewDto {
    private String imageUrl;
    private String summary;
}
