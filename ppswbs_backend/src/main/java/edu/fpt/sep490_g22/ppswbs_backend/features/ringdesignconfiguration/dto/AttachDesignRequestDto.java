package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachDesignRequestDto {

    @NotBlank
    @Size(min = 1, max = 128)
    private String bookingReference;

    @NotBlank
    @Size(min = 1, max = 128)
    private String packageReference;

    @NotBlank
    @Size(min = 1, max = 128)
    private String branchReference;
}

