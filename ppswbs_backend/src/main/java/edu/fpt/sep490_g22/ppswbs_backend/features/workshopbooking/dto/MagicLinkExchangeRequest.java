package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MagicLinkExchangeRequest {

    @NotBlank(message = "token is required")
    private String token;
}
