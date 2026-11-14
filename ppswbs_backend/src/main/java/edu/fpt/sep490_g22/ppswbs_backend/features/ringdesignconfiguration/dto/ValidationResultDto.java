package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationResultDto {

    private boolean valid;

    @Builder.Default
    private List<String> reasons = new ArrayList<>();
}

