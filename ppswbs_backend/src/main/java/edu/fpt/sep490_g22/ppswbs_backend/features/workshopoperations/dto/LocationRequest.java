package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationRequest {

    @NotBlank(message = "Location code is required")
    private String code;

    @NotBlank(message = "Location name is required")
    private String name;

    private String address;

    @Builder.Default
    private Boolean active = true;
}
